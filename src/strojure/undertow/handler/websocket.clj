(ns strojure.undertow.handler.websocket
  "WebSocket handler functionality."
  (:require [strojure.undertow.api.types :as types])
  (:import (io.undertow.websockets WebSocketConnectionCallback)
           (io.undertow.websockets.core WebSocketChannel)
           (io.undertow.websockets.spi WebSocketHttpExchange)
           (org.xnio ChannelListener)
           (strojure.undertow.websocket WebSocketChannelListener)))

(set! *warn-on-reflection* true)

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(defmethod types/as-websocket-listener :default
  [config]
  (WebSocketChannelListener. config))

(defn listener-as-connection-callback
  "Returns connection callback for given `ChannelListener`."
  {:tag WebSocketConnectionCallback}
  [^ChannelListener listener]
  (reify WebSocketConnectionCallback
    (^void onConnect
      [_, ^WebSocketHttpExchange exchange, ^WebSocketChannel chan]
      (when (instance? WebSocketConnectionCallback listener)
        (.onConnect ^WebSocketConnectionCallback listener exchange chan))
      (.set (.getReceiveSetter chan) listener)
      (.resumeReceives chan))))

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
