# frozen_string_literal: true

# Klien kecil API reseller Instanizer. Ruby 3.0+, hanya pustaka standar.
#
#   export INSTANIZER_API_KEY=API_KEY_ANDA
#   ruby instanizer.rb
#
# Contoh ini hanya membaca (saldo, layanan, dan status bila INSTANIZER_ORDER_ID diisi).
# Untuk membuat pesanan, panggil api.add(...) seperti contoh di bagian bawah berkas.

require 'json'
require 'net/http'
require 'uri'

class Instanizer
  class Error < StandardError; end

  DEFAULT_URL = 'https://api.instanizer.com/api/v2'

  def initialize(key, url: ENV.fetch('INSTANIZER_API_URL', DEFAULT_URL))
    @key = key
    @uri = URI(url)
  end

  def services = call(action: 'services')

  def balance = call(action: 'balance')

  # Buat pesanan. Kirim idempotency_key agar pengulangan tidak menagih dua kali.
  def add(service:, link: nil, quantity: nil, idempotency_key: nil, **extra)
    call(action: 'add', service: service, link: link, quantity: quantity,
         idempotency_key: idempotency_key, **extra)
  end

  def status(order) = call(action: 'status', order: order)

  def multi_status(orders) = call(action: 'status', orders: orders.join(','))

  def refill(order) = call(action: 'refill', order: order)

  def refill_status(refill) = call(action: 'refill_status', refill: refill)

  def cancel(orders) = call(action: 'cancel', orders: orders.join(','))

  private

  def call(params)
    form = params.compact.transform_values(&:to_s)
    form['key'] = @key # key hanya di body, bukan di query string

    begin
      response = Net::HTTP.start(@uri.host, @uri.port, use_ssl: @uri.scheme == 'https',
                                                       read_timeout: 60, open_timeout: 15) do |http|
        http.request(Net::HTTP::Post.new(@uri).tap { |req| req.set_form_data(form) })
      end
    rescue SocketError, SystemCallError, IOError, Timeout::Error => e
      raise Error, "Koneksi gagal: #{e.message}"
    end

    begin
      data = JSON.parse(response.body.to_s)
    rescue JSON::ParserError
      raise Error, 'Respons bukan JSON yang valid.'
    end
    # API membalas HTTP 200 untuk galat: periksa field "error".
    raise Error, data['error'] if data.is_a?(Hash) && data['error'].is_a?(String)

    data
  end
end

if $PROGRAM_NAME == __FILE__
  key = ENV['INSTANIZER_API_KEY']
  abort 'Isi INSTANIZER_API_KEY terlebih dahulu.' if key.nil? || key.empty?

  api = Instanizer.new(key)
  begin
    puts "Saldo: #{api.balance}"
    services = api.services
    puts "#{services.length} layanan. Tiga pertama:"
    services.first(3).each do |s|
      puts "  ##{s['service']} #{s['name']} - Rp #{s['rate']}/1000 (min #{s['min']}, max #{s['max']})"
    end

    order_id = ENV['INSTANIZER_ORDER_ID']
    puts "Status pesanan: #{api.status(order_id)}" if order_id && !order_id.empty?

    # Membuat pesanan (memotong saldo). Contoh:
    # result = api.add(service: 1, link: 'https://instagram.com/username',
    #                  quantity: 1000, idempotency_key: 'pesanan-0001')
    # puts "Order ID: #{result['order']}"
  rescue Instanizer::Error => e
    abort "Gagal: #{e.message}"
  end
end
