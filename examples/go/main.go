// Minimal Instanizer reseller API client. Go 1.20+, standard library only.
//
//	export INSTANIZER_API_KEY=YOUR_API_KEY
//	go run .
//
// This example is read-only (balance, services, and order status when INSTANIZER_ORDER_ID is set).
// To place an order, call client.Add(...) as shown at the bottom of this file.
package main

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"strings"
	"time"
)

const defaultURL = "https://api.instanizer.com/api/v2"

// Client calls POST /api/v2.
type Client struct {
	Key  string
	URL  string
	HTTP *http.Client
}

// NewClient creates a client. The URL can be overridden via INSTANIZER_API_URL.
func NewClient(key string) *Client {
	u := os.Getenv("INSTANIZER_API_URL")
	if u == "" {
		u = defaultURL
	}
	return &Client{Key: key, URL: u, HTTP: &http.Client{Timeout: 60 * time.Second}}
}

// APIError is an error returned by the API. The API replies HTTP 200 with {"error": "..."}.
type APIError struct{ Message string }

func (e *APIError) Error() string { return e.Message }

// call sends an action and returns the raw JSON.
func (c *Client) call(params url.Values) (json.RawMessage, error) {
	params.Set("key", c.Key) // key goes in the body only, never in the query string
	resp, err := c.HTTP.Post(c.URL, "application/x-www-form-urlencoded", strings.NewReader(params.Encode()))
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	raw, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}

	// Check the "error" field; an array response cannot be unmarshaled into this struct.
	var probe struct {
		Error string `json:"error"`
	}
	if json.Unmarshal(raw, &probe) == nil && probe.Error != "" {
		return nil, &APIError{Message: probe.Error}
	}
	return raw, nil
}

// Service is a single catalog entry.
type Service struct {
	Service  int    `json:"service"`
	Name     string `json:"name"`
	Type     string `json:"type"`
	Category string `json:"category"`
	Rate     string `json:"rate"` // Rupiah per 1,000
	Min      string `json:"min"`
	Max      string `json:"max"`
	Refill   bool   `json:"refill"`
	Cancel   bool   `json:"cancel"`
}

func (c *Client) Services() ([]Service, error) {
	raw, err := c.call(url.Values{"action": {"services"}})
	if err != nil {
		return nil, err
	}
	var out []Service
	if err := json.Unmarshal(raw, &out); err != nil {
		return nil, err
	}
	return out, nil
}

func (c *Client) Balance() (json.RawMessage, error) {
	return c.call(url.Values{"action": {"balance"}})
}

// Add places an order. Pass idempotencyKey so a retry is not billed twice.
func (c *Client) Add(service int, link string, quantity int, idempotencyKey string) (json.RawMessage, error) {
	p := url.Values{
		"action":   {"add"},
		"service":  {fmt.Sprint(service)},
		"link":     {link},
		"quantity": {fmt.Sprint(quantity)},
	}
	if idempotencyKey != "" {
		p.Set("idempotency_key", idempotencyKey)
	}
	return c.call(p)
}

func (c *Client) Status(order string) (json.RawMessage, error) {
	return c.call(url.Values{"action": {"status"}, "order": {order}})
}

// MultiStatus takes comma-separated IDs, at most 100.
func (c *Client) MultiStatus(orders string) (json.RawMessage, error) {
	return c.call(url.Values{"action": {"status"}, "orders": {orders}})
}

func (c *Client) Refill(order string) (json.RawMessage, error) {
	return c.call(url.Values{"action": {"refill"}, "order": {order}})
}

func (c *Client) RefillStatus(refill string) (json.RawMessage, error) {
	return c.call(url.Values{"action": {"refill_status"}, "refill": {refill}})
}

func (c *Client) Cancel(orders string) (json.RawMessage, error) {
	return c.call(url.Values{"action": {"cancel"}, "orders": {orders}})
}

func main() {
	key := os.Getenv("INSTANIZER_API_KEY")
	if key == "" {
		fmt.Fprintln(os.Stderr, "Set INSTANIZER_API_KEY first.")
		os.Exit(1)
	}
	client := NewClient(key)

	balance, err := client.Balance()
	if err != nil {
		fail(err)
	}
	fmt.Println("Balance:", string(balance))

	services, err := client.Services()
	if err != nil {
		fail(err)
	}
	fmt.Printf("%d services. First three:\n", len(services))
	for i, s := range services {
		if i == 3 {
			break
		}
		fmt.Printf("  #%d %s - Rp %s/1000 (min %s, max %s)\n", s.Service, s.Name, s.Rate, s.Min, s.Max)
	}

	if orderID := os.Getenv("INSTANIZER_ORDER_ID"); orderID != "" {
		status, err := client.Status(orderID)
		if err != nil {
			fail(err)
		}
		fmt.Println("Order status:", string(status))
	}

	// Place an order (deducts balance). Example:
	// result, err := client.Add(1, "https://instagram.com/username", 1000, "order-0001")
	// fmt.Println(string(result)) // {"order":23501}
}

func fail(err error) {
	fmt.Fprintln(os.Stderr, "Failed:", err)
	os.Exit(1)
}
