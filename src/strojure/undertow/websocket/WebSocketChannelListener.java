package strojure.undertow.websocket;

import clojure.lang.IFn;
import clojure.lang.ILookup;
import clojure.lang.Keyword;
import clojure.lang.RT;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.core.*;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import org.xnio.Pooled;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Websocket channel listener implementation configured by Clojure map with
 * optional keys `:on-connect`, `:on-message`, `:on-close` and `:on-error`.
 *
 * <p>Implemented in Java because Clojure's proxy is slow and does not allow to
 * call super methods.
 */
public class WebSocketChannelListener extends AbstractReceiveListener implements WebSocketConnectionCallback {
  private final IFn onConnect;
  private final IFn onMessage;
  private final IFn onClose;
  private final IFn onError;

  private static final Keyword k_callback = RT.keyword(null, "callback");
  private static final Keyword k_channel = RT.keyword(null, "channel");
  private static final Keyword k_text = RT.keyword(null, "text");
  private static final Keyword k_data = RT.keyword(null, "data");
  private static final Keyword k_onMessage = RT.keyword(null, "on-message");

  public WebSocketChannelListener(ILookup config) {
    this.onConnect = (IFn) config.valAt(RT.keyword(null, "on-connect"));
    this.onMessage = (IFn) config.valAt(RT.keyword(null, "on-message"));
    this.onClose = (IFn) config.valAt(RT.keyword(null, "on-close"));
    this.onError = (IFn) config.valAt(RT.keyword(null, "on-error"));
  }

  @Override
  public void onConnect(WebSocketHttpExchange exchange,
                        WebSocketChannel channel) {
    if (onConnect != null) {
      onConnect.invoke(RT.map(k_callback, RT.keyword(null, "on-connect"),
                              RT.keyword(null, "exchange"), exchange,
                              k_channel, channel));
    }
  }

  @Override
  protected void onFullTextMessage(WebSocketChannel channel,
                                   BufferedTextMessage text) throws IOException {
    if (this.onMessage == null)
      super.onFullTextMessage(channel, text);
    else
      onMessage.invoke(RT.map(k_callback, k_onMessage,
                              k_channel, channel,
                              k_text, text.getData()));
  }

  @Override
  protected void onFullBinaryMessage(WebSocketChannel channel,
                                     BufferedBinaryMessage binary) throws IOException {
    if (this.onMessage == null)
      super.onFullBinaryMessage(channel, binary);
    else {
      @SuppressWarnings("deprecation")
      Pooled<ByteBuffer[]> pooled = binary.getData();
      byte[] buffer = WebSockets.mergeBuffers(pooled.getResource()).array();
      byte[] data = Arrays.copyOf(buffer, buffer.length);
      pooled.free();
      onMessage.invoke(RT.map(k_callback, k_onMessage,
                              k_channel, channel,
                              k_data, data));
    }
  }

  @Override
  protected void onCloseMessage(CloseMessage cm,
                                WebSocketChannel channel) {
    if (this.onError == null)
      super.onCloseMessage(cm, channel);
    else
      onClose.invoke(RT.map(k_callback, RT.keyword(null, "on-close"),
                            k_channel, channel,
                            RT.keyword(null, "code"), cm.getCode(),
                            RT.keyword(null, "reason"), cm.getReason()));
  }

  @Override
  protected void onError(WebSocketChannel channel,
                         Throwable error) {
    if (this.onError == null)
      super.onError(channel, error);
    else
      this.onError.invoke(RT.map(k_callback, RT.keyword(null, "on-error"),
                                 k_channel, channel,
                                 RT.keyword(null, "error"), error));
  }

}
