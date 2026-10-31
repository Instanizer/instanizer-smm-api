// Minimal Instanizer reseller API client. .NET 8+, no extra packages.
//
//   export INSTANIZER_API_KEY=YOUR_API_KEY
//   dotnet run
//
// This example is read-only (balance, services, and order status when INSTANIZER_ORDER_ID is set).
// To place an order, call api.AddAsync(...) as shown at the bottom of this file.

using System.Text.Json;

var key = Environment.GetEnvironmentVariable("INSTANIZER_API_KEY");
if (string.IsNullOrWhiteSpace(key))
{
    Console.Error.WriteLine("Set INSTANIZER_API_KEY first.");
    return 1;
}

using var api = new InstanizerClient(key);

try
{
    Console.WriteLine($"Balance: {await api.BalanceAsync()}");

    var services = await api.ServicesAsync();
    Console.WriteLine($"{services.GetArrayLength()} services. First three:");
    foreach (var s in services.EnumerateArray().Take(3))
    {
        Console.WriteLine(
            $"  #{s.GetProperty("service").GetInt32()} {s.GetProperty("name").GetString()} - " +
            $"Rp {s.GetProperty("rate").GetString()}/1000 " +
            $"(min {s.GetProperty("min").GetString()}, max {s.GetProperty("max").GetString()})");
    }

    var orderId = Environment.GetEnvironmentVariable("INSTANIZER_ORDER_ID");
    if (!string.IsNullOrWhiteSpace(orderId))
    {
        Console.WriteLine($"Order status: {await api.StatusAsync(orderId)}");
    }

    // Place an order (deducts balance). Example:
    // var result = await api.AddAsync(1, "https://instagram.com/username", 1000, "order-0001");
    // Console.WriteLine($"Order ID: {result.GetProperty("order").GetInt32()}");
}
catch (InstanizerException e)
{
    Console.Error.WriteLine($"Failed: {e.Message}");
    return 1;
}

return 0;

public class InstanizerException(string message) : Exception(message);

public sealed class InstanizerClient : IDisposable
{
    private readonly HttpClient _http = new() { Timeout = TimeSpan.FromSeconds(60) };
    private readonly string _key;
    private readonly string _url;

    public InstanizerClient(string key, string? url = null)
    {
        _key = key;
        _url = url
            ?? Environment.GetEnvironmentVariable("INSTANIZER_API_URL")
            ?? "https://api.instanizer.com/api/v2";
    }

    private async Task<JsonElement> CallAsync(Dictionary<string, string> parameters)
    {
        // key goes in the body only, never in the query string
        var form = new Dictionary<string, string>(parameters) { ["key"] = _key };
        using var response = await _http.PostAsync(_url, new FormUrlEncodedContent(form));
        var json = await response.Content.ReadAsStringAsync();
        using var doc = JsonDocument.Parse(json);
        var root = doc.RootElement.Clone();

        // The API replies HTTP 200 for errors: check the "error" field.
        if (root.ValueKind == JsonValueKind.Object && root.TryGetProperty("error", out var error)
            && error.ValueKind == JsonValueKind.String)
        {
            throw new InstanizerException(error.GetString() ?? "Unknown error");
        }
        return root;
    }

    public Task<JsonElement> ServicesAsync() => CallAsync(new() { ["action"] = "services" });

    public Task<JsonElement> BalanceAsync() => CallAsync(new() { ["action"] = "balance" });

    /// <summary>Place an order. Pass idempotencyKey so a retry is not billed twice.</summary>
    public Task<JsonElement> AddAsync(int service, string link, int quantity, string? idempotencyKey = null)
    {
        var p = new Dictionary<string, string>
        {
            ["action"] = "add",
            ["service"] = service.ToString(),
            ["link"] = link,
            ["quantity"] = quantity.ToString(),
        };
        if (idempotencyKey is not null) p["idempotency_key"] = idempotencyKey;
        return CallAsync(p);
    }

    public Task<JsonElement> StatusAsync(string order) =>
        CallAsync(new() { ["action"] = "status", ["order"] = order });

    public Task<JsonElement> MultiStatusAsync(IEnumerable<string> orders) =>
        CallAsync(new() { ["action"] = "status", ["orders"] = string.Join(",", orders) });

    public Task<JsonElement> RefillAsync(string order) =>
        CallAsync(new() { ["action"] = "refill", ["order"] = order });

    public Task<JsonElement> RefillStatusAsync(string refill) =>
        CallAsync(new() { ["action"] = "refill_status", ["refill"] = refill });

    public Task<JsonElement> CancelAsync(IEnumerable<string> orders) =>
        CallAsync(new() { ["action"] = "cancel", ["orders"] = string.Join(",", orders) });

    public void Dispose() => _http.Dispose();
}
