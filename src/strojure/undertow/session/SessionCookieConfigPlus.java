package strojure.undertow.session;

import io.undertow.UndertowLogger;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.CookieImpl;
import io.undertow.server.session.SessionCookieConfig;

/**
 * Modified version of the {@link SessionCookieConfig} with SameSite attribute.
 */
public class SessionCookieConfigPlus extends SessionCookieConfig
{
  private String sameSiteMode;

  @Override public void setSessionId (final HttpServerExchange exchange,
                                      final String sessionId) {
    Cookie cookie = new CookieImpl( getCookieName(), sessionId );
    cookie.setPath( getPath() )
          .setDomain( getDomain() )
          .setDiscard( isDiscard() )
          .setSecure( isSecure() )
          .setHttpOnly( isHttpOnly() )
          .setComment( getComment() );
    if (null != sameSiteMode) {
      cookie.setSameSiteMode( sameSiteMode );
    }
    if (getMaxAge() > 0) {
      cookie.setMaxAge( getMaxAge() );
    }
    exchange.setResponseCookie( cookie );
    UndertowLogger.SESSION_LOGGER.tracef( "Setting session cookie session id %s on %s", sessionId, exchange );
  }

  public String getSameSiteMode () {
    return sameSiteMode;
  }

  public SessionCookieConfigPlus setSameSiteMode (final String mode) {
    this.sameSiteMode = mode;
    return this;
  }

}
