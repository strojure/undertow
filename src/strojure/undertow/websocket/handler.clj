(ns strojure.undertow.websocket.handler
  "WebSocket connection handler."
  (:require [strojure.undertow.api.types :as types])
  (:import (io.undertow.websockets WebSocketConnectionCallback WebSocketProtocolHandshakeHandler)
           (io.undertow.websockets.core WebSocketChannel)
           (io.undertow.websockets.spi WebSocketHttpExchange)
           (org.xnio ChannelListener)
           (strojure.undertow.websocket WebSocketChannelListener)))

(set! *warn-on-reflection* true)

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(defmethod types/as-websocket-listener :default
  [config]
  (WebSocketChannelListener. config))

(defmethod types/as-websocket-connection-callback ChannelListener
  [listener]
  (reify WebSocketConnectionCallback
    (^void onConnect
      [_, ^WebSocketHttpExchange exchange, ^WebSocketChannel chan]
      (when (instance? WebSocketConnectionCallback listener)
        (.onConnect ^WebSocketConnectionCallback listener exchange chan))
      (.set (.getReceiveSetter chan) listener)
      (.resumeReceives chan))))

(prefer-method types/as-websocket-connection-callback
               ChannelListener WebSocketConnectionCallback)

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(defn handshake
  "Returns a new web socket session handler with optional next handler to invoke
  if the web socket connection fails. A `HttpHandler` which will process the
  `HttpServerExchange` and do the actual handshake/upgrade to WebSocket.

  Function arguments:

  - `next-handler` The handler that is invoked if there are no web socket
                   headers.

  - `callback` The instance of the `WebSocketConnectionCallback` or callback
               configuration map.

    Callback configuration options:

      - `:on-connect` The function `(fn [{:keys [callback exchange channel]}])`.
          + Is called once the WebSocket connection is established, which means
            the handshake was successful.

      - `:on-message` The function `(fn [{:keys [callback channel text data]}])`.
          + Is called when listener receives a message.
          + The text message is provided in `:text` and binary message is
            provided in `:data`.

      - `:on-close` The function `(fn [{:keys [callback channel code reason]}])`.
          + Is called once the WebSocket connection is closed.
          + The `:code` is status code to close messages:
            http://tools.ietf.org/html/rfc6455#section-7.4

      - `:on-error` The function `(fn [{:keys [callback channel error]}])`.
          + Is called on WebSocket connection error.
          + Default implementation just closes WebSocket connection.
  "
  {:arglists '([{:as callback :keys [on-connect, on-message, on-close, on-error]}]
               [next-handler, {:as callback :keys [on-connect, on-message, on-close, on-error]}]
               [callback]
               [next-handler, callback])}
  (^WebSocketProtocolHandshakeHandler
   [callback]
   (WebSocketProtocolHandshakeHandler. (types/as-websocket-connection-callback callback)))
  (^WebSocketProtocolHandshakeHandler
   [next-handler, callback]
   (WebSocketProtocolHandshakeHandler. (types/as-websocket-connection-callback callback)
                                       (types/as-handler next-handler))))

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
