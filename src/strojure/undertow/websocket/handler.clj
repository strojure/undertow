(ns strojure.undertow.websocket.handler
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
