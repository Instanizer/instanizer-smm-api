<?php
/**
 * Minimal Instanizer reseller API client. PHP 7.4+ with the curl and json extensions.
 *
 *   export INSTANIZER_API_KEY=YOUR_API_KEY
 *   php instanizer.php
 *
 * This example is read-only (balance, services, and order status when INSTANIZER_ORDER_ID is set).
 * To place an order, call $api->add(...) as shown at the bottom of this file.
 */

class InstanizerException extends RuntimeException
{
}

class Instanizer
{
    private string $key;
    private string $url;

    public function __construct(string $key, ?string $url = null)
    {
        $this->key = $key;
        $this->url = $url ?? (getenv('INSTANIZER_API_URL') ?: 'https://api.instanizer.com/api/v2');
    }

    private function call(array $params)
    {
        $params = array_filter($params, fn ($v) => $v !== null);
        $params['key'] = $this->key; // key goes in the body only, never in the query string

        $ch = curl_init($this->url);
        curl_setopt_array($ch, [
            CURLOPT_POST => true,
            CURLOPT_POSTFIELDS => http_build_query($params),
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_TIMEOUT => 60,
        ]);
        $raw = curl_exec($ch);
        if ($raw === false) {
            $message = curl_error($ch);
            curl_close($ch);
            throw new InstanizerException("Connection failed: $message");
        }
        curl_close($ch);

        $data = json_decode($raw, true);
        if ($data === null) {
            throw new InstanizerException('Response is not valid JSON.');
        }
        // The API replies HTTP 200 for errors: check the "error" field.
        if (is_array($data) && isset($data['error']) && is_string($data['error'])) {
            throw new InstanizerException($data['error']);
        }
        return $data;
    }

    public function services(): array
    {
        return $this->call(['action' => 'services']);
    }

    public function balance(): array
    {
        return $this->call(['action' => 'balance']);
    }

    /** Place an order. Pass idempotency_key so a retry is not billed twice. */
    public function add(array $data): array
    {
        return $this->call(array_merge(['action' => 'add'], $data));
    }

    public function status($orderId): array
    {
        return $this->call(['action' => 'status', 'order' => $orderId]);
    }

    public function multiStatus(array $orderIds): array
    {
        return $this->call(['action' => 'status', 'orders' => implode(',', $orderIds)]);
    }

    public function refill($orderId): array
    {
        return $this->call(['action' => 'refill', 'order' => $orderId]);
    }

    public function refillStatus($refillId): array
    {
        return $this->call(['action' => 'refill_status', 'refill' => $refillId]);
    }

    public function cancel(array $orderIds): array
    {
        return $this->call(['action' => 'cancel', 'orders' => implode(',', $orderIds)]);
    }
}

// Run only when this file is executed directly (not when included).
if (PHP_SAPI === 'cli' && realpath($_SERVER['SCRIPT_FILENAME'] ?? '') === __FILE__) {
    $key = getenv('INSTANIZER_API_KEY');
    if (!$key) {
        fwrite(STDERR, "Set INSTANIZER_API_KEY first.\n");
        exit(1);
    }
    $api = new Instanizer($key);

    try {
        echo 'Balance: ' . json_encode($api->balance()) . "\n";
        $services = $api->services();
        echo count($services) . " services. First three:\n";
        foreach (array_slice($services, 0, 3) as $s) {
            echo "  #{$s['service']} {$s['name']} - Rp {$s['rate']}/1000 (min {$s['min']}, max {$s['max']})\n";
        }

        $orderId = getenv('INSTANIZER_ORDER_ID');
        if ($orderId) {
            echo 'Order status: ' . json_encode($api->status($orderId)) . "\n";
        }

        // Place an order (deducts balance). Example:
        // $result = $api->add([
        //     'service' => 1,
        //     'link' => 'https://instagram.com/username',
        //     'quantity' => 1000,
        //     'idempotency_key' => 'order-0001',
        // ]);
        // echo 'Order ID: ' . $result['order'] . "\n";
    } catch (InstanizerException $e) {
        fwrite(STDERR, 'Failed: ' . $e->getMessage() . "\n");
        exit(1);
    }
}
