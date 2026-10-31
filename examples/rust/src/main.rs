//! Minimal Instanizer reseller API client. Rust 1.70+, dependencies: ureq and serde_json.
//!
//!   export INSTANIZER_API_KEY=YOUR_API_KEY
//!   cargo run
//!
//! This example is read-only (balance, services, and order status when INSTANIZER_ORDER_ID is set).
//! To place an order, call client.add(...) as shown at the bottom of this file.

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

    /// Send an action to the API. The API replies HTTP 200 for errors: the "error" field is turned into an Err.
    fn call(&self, params: &[(&str, &str)]) -> Result<Value, Box<dyn Error>> {
        let mut form: Vec<(&str, &str)> = params.to_vec();
        form.push(("key", &self.key)); // key goes in the body only, never in the query string
        let body: Value = self.agent.post(&self.url).send_form(&form)?.into_json()?;

        if let Some(message) = body.get("error").and_then(Value::as_str) {
            return Err(format!("API error: {message}").into());
        }
        Ok(body)
    }

    fn services(&self) -> Result<Value, Box<dyn Error>> {
        self.call(&[("action", "services")])
    }

    fn balance(&self) -> Result<Value, Box<dyn Error>> {
        self.call(&[("action", "balance")])
    }

    /// Place an order. Pass idempotency_key so a retry is not billed twice.
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

    /// orders: comma-separated IDs, at most 100, e.g. "23501,23502".
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
    let key = env::var("INSTANIZER_API_KEY").map_err(|_| "Set INSTANIZER_API_KEY first.")?;
    let client = Client::new(key);

    println!("Balance: {}", client.balance()?);
    let services = client.services()?;
    let list = services.as_array().ok_or("Services response is not an array")?;
    println!("{} services. First three:", list.len());
    for s in list.iter().take(3) {
        println!(
            "  #{} {} - Rp {}/1000 (min {}, max {})",
            s["service"], s["name"].as_str().unwrap_or(""), s["rate"].as_str().unwrap_or(""),
            s["min"].as_str().unwrap_or(""), s["max"].as_str().unwrap_or("")
        );
    }

    if let Ok(order_id) = env::var("INSTANIZER_ORDER_ID") {
        if !order_id.is_empty() {
            println!("Order status: {}", client.status(&order_id)?);
        }
    }

    // Place an order (deducts balance). Example:
    // let result = client.add(1, "https://instagram.com/username", 1000, Some("order-0001"))?;
    // println!("Order ID: {}", result["order"]);
    Ok(())
}

fn main() {
    if let Err(e) = run() {
        eprintln!("Failed: {e}");
        std::process::exit(1);
    }
}
