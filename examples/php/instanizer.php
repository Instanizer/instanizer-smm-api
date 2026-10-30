<?php
/**
 * Klien kecil API reseller Instanizer. PHP 7.4+ dengan ekstensi curl dan json.
 *
 *   export INSTANIZER_API_KEY=API_KEY_ANDA
 *   php instanizer.php
 *
 * Contoh ini hanya membaca (saldo, layanan, dan status bila INSTANIZER_ORDER_ID diisi).
 * Untuk membuat pesanan, panggil $api->add(...) seperti contoh di bagian bawah berkas.
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
        $params['key'] = $this->key; // key hanya di body, bukan di query string

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
            throw new InstanizerException("Koneksi gagal: $message");
        }
        curl_close($ch);

        $data = json_decode($raw, true);
        if ($data === null) {
            throw new InstanizerException('Respons bukan JSON yang valid.');
        }
        // API membalas HTTP 200 untuk galat: periksa field "error".
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

    /** Buat pesanan. Kirim idempotency_key agar pengulangan tidak menagih dua kali. */
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

// Jalankan hanya bila berkas ini dieksekusi langsung (bukan saat di-include).
if (PHP_SAPI === 'cli' && realpath($_SERVER['SCRIPT_FILENAME'] ?? '') === __FILE__) {
    $key = getenv('INSTANIZER_API_KEY');
    if (!$key) {
        fwrite(STDERR, "Isi INSTANIZER_API_KEY terlebih dahulu.\n");
        exit(1);
    }
    $api = new Instanizer($key);

    try {
        echo 'Saldo: ' . json_encode($api->balance()) . "\n";
        $services = $api->services();
        echo count($services) . " layanan. Tiga pertama:\n";
        foreach (array_slice($services, 0, 3) as $s) {
            echo "  #{$s['service']} {$s['name']} - Rp {$s['rate']}/1000 (min {$s['min']}, max {$s['max']})\n";
        }

        $orderId = getenv('INSTANIZER_ORDER_ID');
        if ($orderId) {
            echo 'Status pesanan: ' . json_encode($api->status($orderId)) . "\n";
        }

        // Membuat pesanan (memotong saldo). Contoh:
        // $result = $api->add([
        //     'service' => 1,
        //     'link' => 'https://instagram.com/username',
        //     'quantity' => 1000,
        //     'idempotency_key' => 'pesanan-0001',
        // ]);
        // echo 'Order ID: ' . $result['order'] . "\n";
    } catch (InstanizerException $e) {
        fwrite(STDERR, 'Gagal: ' . $e->getMessage() . "\n");
        exit(1);
    }
}
