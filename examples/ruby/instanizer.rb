# frozen_string_literal: true

# Minimal Instanizer reseller API client. Ruby 3.0+, standard library only.
#
#   export INSTANIZER_API_KEY=YOUR_API_KEY
#   ruby instanizer.rb
#
# This example is read-only (balance, services, and order status when INSTANIZER_ORDER_ID is set).
# To place an order, call api.add(...) as shown at the bottom of this file.

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

  # Place an order. Pass idempotency_key so a retry is not billed twice.
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
    form['key'] = @key # key goes in the body only, never in the query string

    begin
      response = Net::HTTP.start(@uri.host, @uri.port, use_ssl: @uri.scheme == 'https',
                                                       read_timeout: 60, open_timeout: 15) do |http|
        http.request(Net::HTTP::Post.new(@uri).tap { |req| req.set_form_data(form) })
      end
    rescue SocketError, SystemCallError, IOError, Timeout::Error => e
      raise Error, "Connection failed: #{e.message}"
    end

    begin
      data = JSON.parse(response.body.to_s)
    rescue JSON::ParserError
      raise Error, 'Response is not valid JSON.'
    end
    # The API replies HTTP 200 for errors: check the "error" field.
    raise Error, data['error'] if data.is_a?(Hash) && data['error'].is_a?(String)

    data
  end
end

if $PROGRAM_NAME == __FILE__
  key = ENV['INSTANIZER_API_KEY']
  abort 'Set INSTANIZER_API_KEY first.' if key.nil? || key.empty?

  api = Instanizer.new(key)
  begin
    puts "Balance: #{api.balance}"
    services = api.services
    puts "#{services.length} services. First three:"
    services.first(3).each do |s|
      puts "  ##{s['service']} #{s['name']} - Rp #{s['rate']}/1000 (min #{s['min']}, max #{s['max']})"
    end

    order_id = ENV['INSTANIZER_ORDER_ID']
    puts "Order status: #{api.status(order_id)}" if order_id && !order_id.empty?

    # Place an order (deducts balance). Example:
    # result = api.add(service: 1, link: 'https://instagram.com/username',
    #                  quantity: 1000, idempotency_key: 'order-0001')
    # puts "Order ID: #{result['order']}"
  rescue Instanizer::Error => e
    abort "Failed: #{e.message}"
  end
end
