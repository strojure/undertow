(ns strojure.undertow.handler.websocket
  "WebSocket handler functionality."
  (:import (io.undertow.websockets WebSocketConnectionCallback)
           (io.undertow.websockets.core WebSocketChannel)
           (io.undertow.websockets.spi WebSocketHttpExchange)
           (org.xnio ChannelListener)))

(set! *warn-on-reflection* true)

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

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
