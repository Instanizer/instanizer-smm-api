//! Klien kecil API reseller Instanizer. Rust 1.70+, dependensi: ureq dan serde_json.
//!
//!   export INSTANIZER_API_KEY=API_KEY_ANDA
//!   cargo run
//!
//! Contoh ini hanya membaca (saldo, layanan, dan status bila INSTANIZER_ORDER_ID diisi).
//! Untuk membuat pesanan, panggil client.add(...) seperti contoh di bagian bawah berkas.

use serde_json::Value;
use std::env;
use std::error::Error;
use std::time::Duration;

const DEFAULT_URL: &str = "https://api.instanizer.com/api/v2";

struct Client {
    key: String,
    url: String,
    agent: ureq::Agent,
}

impl Client {
    fn new(key: String) -> Self {
        let url = env::var("INSTANIZER_API_URL").unwrap_or_else(|_| DEFAULT_URL.to_string());
        let agent = ureq::AgentBuilder::new().timeout(Duration::from_secs(60)).build();
        Client { key, url, agent }
    }

    /// Kirim aksi ke API. API membalas HTTP 200 untuk galat: field "error" diubah menjadi Err.
    fn call(&self, params: &[(&str, &str)]) -> Result<Value, Box<dyn Error>> {
        let mut form: Vec<(&str, &str)> = params.to_vec();
        form.push(("key", &self.key)); // key hanya di body, bukan di query string
        let body: Value = self.agent.post(&self.url).send_form(&form)?.into_json()?;

        if let Some(message) = body.get("error").and_then(Value::as_str) {
            return Err(format!("Galat API: {message}").into());
        }
        Ok(body)
    }

    fn services(&self) -> Result<Value, Box<dyn Error>> {
        self.call(&[("action", "services")])
    }

    fn balance(&self) -> Result<Value, Box<dyn Error>> {
        self.call(&[("action", "balance")])
    }

    /// Buat pesanan. Kirim idempotency_key agar pengulangan tidak menagih dua kali.
    #[allow(dead_code)]
    fn add(
        &self,
        service: u32,
        link: &str,
        quantity: u32,
        idempotency_key: Option<&str>,
    ) -> Result<Value, Box<dyn Error>> {
        let service = service.to_string();
        let quantity = quantity.to_string();
        let mut params = vec![
            ("action", "add"),
            ("service", service.as_str()),
            ("link", link),
            ("quantity", quantity.as_str()),
        ];
        if let Some(k) = idempotency_key {
            params.push(("idempotency_key", k));
        }
        self.call(&params)
    }

    fn status(&self, order: &str) -> Result<Value, Box<dyn Error>> {
        self.call(&[("action", "status"), ("order", order)])
    }

    /// orders: ID dipisah koma, maksimal 100, mis. "23501,23502".
    #[allow(dead_code)]
    fn multi_status(&self, orders: &str) -> Result<Value, Box<dyn Error>> {
        self.call(&[("action", "status"), ("orders", orders)])
    }

    #[allow(dead_code)]
    fn refill(&self, order: &str) -> Result<Value, Box<dyn Error>> {
        self.call(&[("action", "refill"), ("order", order)])
    }

    #[allow(dead_code)]
    fn refill_status(&self, refill: &str) -> Result<Value, Box<dyn Error>> {
        self.call(&[("action", "refill_status"), ("refill", refill)])
    }

    #[allow(dead_code)]
    fn cancel(&self, orders: &str) -> Result<Value, Box<dyn Error>> {
        self.call(&[("action", "cancel"), ("orders", orders)])
    }
}

fn run() -> Result<(), Box<dyn Error>> {
    let key = env::var("INSTANIZER_API_KEY").map_err(|_| "Isi INSTANIZER_API_KEY terlebih dahulu.")?;
    let client = Client::new(key);

    println!("Saldo: {}", client.balance()?);
    let services = client.services()?;
    let list = services.as_array().ok_or("Respons layanan bukan array")?;
    println!("{} layanan. Tiga pertama:", list.len());
    for s in list.iter().take(3) {
        println!(
            "  #{} {} - Rp {}/1000 (min {}, max {})",
            s["service"], s["name"].as_str().unwrap_or(""), s["rate"].as_str().unwrap_or(""),
            s["min"].as_str().unwrap_or(""), s["max"].as_str().unwrap_or("")
        );
    }

    if let Ok(order_id) = env::var("INSTANIZER_ORDER_ID") {
        if !order_id.is_empty() {
            println!("Status pesanan: {}", client.status(&order_id)?);
        }
    }

    // Membuat pesanan (memotong saldo). Contoh:
    // let result = client.add(1, "https://instagram.com/username", 1000, Some("pesanan-0001"))?;
    // println!("Order ID: {}", result["order"]);
    Ok(())
}

fn main() {
    if let Err(e) = run() {
        eprintln!("Gagal: {e}");
        std::process::exit(1);
    }
}
