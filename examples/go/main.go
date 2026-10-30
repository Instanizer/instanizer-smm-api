// Klien kecil API reseller Instanizer. Go 1.20+, hanya pustaka standar.
//
//	export INSTANIZER_API_KEY=API_KEY_ANDA
//	go run .
//
// Contoh ini hanya membaca (saldo, layanan, dan status bila INSTANIZER_ORDER_ID diisi).
// Untuk membuat pesanan, panggil client.Add(...) seperti contoh di bagian bawah berkas.
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

// Client memanggil POST /api/v2.
type Client struct {
	Key  string
	URL  string
	HTTP *http.Client
}

// NewClient membuat klien. URL dapat diganti lewat INSTANIZER_API_URL.
func NewClient(key string) *Client {
	u := os.Getenv("INSTANIZER_API_URL")
	if u == "" {
		u = defaultURL
	}
	return &Client{Key: key, URL: u, HTTP: &http.Client{Timeout: 60 * time.Second}}
}

// APIError adalah galat dari API. API membalas HTTP 200 dengan {"error": "..."}.
type APIError struct{ Message string }

func (e *APIError) Error() string { return e.Message }

// call mengirim aksi dan mengembalikan JSON mentah.
func (c *Client) call(params url.Values) (json.RawMessage, error) {
	params.Set("key", c.Key) // key hanya di body, bukan di query string
	resp, err := c.HTTP.Post(c.URL, "application/x-www-form-urlencoded", strings.NewReader(params.Encode()))
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	raw, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}

	// Periksa field "error"; respons berupa array tidak bisa di-unmarshal ke struct ini.
	var probe struct {
		Error string `json:"error"`
	}
	if json.Unmarshal(raw, &probe) == nil && probe.Error != "" {
		return nil, &APIError{Message: probe.Error}
	}
	return raw, nil
}

// Service adalah satu entri katalog.
type Service struct {
	Service  int    `json:"service"`
	Name     string `json:"name"`
	Type     string `json:"type"`
	Category string `json:"category"`
	Rate     string `json:"rate"` // Rupiah per 1.000
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

// Add membuat pesanan. Kirim idempotencyKey agar pengulangan tidak menagih dua kali.
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

// MultiStatus menerima ID dipisah koma, maksimal 100.
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
		fmt.Fprintln(os.Stderr, "Isi INSTANIZER_API_KEY terlebih dahulu.")
		os.Exit(1)
	}
	client := NewClient(key)

	balance, err := client.Balance()
	if err != nil {
		fail(err)
	}
	fmt.Println("Saldo:", string(balance))

	services, err := client.Services()
	if err != nil {
		fail(err)
	}
	fmt.Printf("%d layanan. Tiga pertama:\n", len(services))
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
		fmt.Println("Status pesanan:", string(status))
	}

	// Membuat pesanan (memotong saldo). Contoh:
	// result, err := client.Add(1, "https://instagram.com/username", 1000, "pesanan-0001")
	// fmt.Println(string(result)) // {"order":23501}
}

func fail(err error) {
	fmt.Fprintln(os.Stderr, "Gagal:", err)
	os.Exit(1)
}
