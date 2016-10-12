package de.volkerGronau.distributedClassroom;

import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.btr.proxy.search.ProxySearch;
import com.btr.proxy.search.ProxySearch.Strategy;
import com.btr.proxy.util.Logger.LogBackEnd;
import com.btr.proxy.util.Logger.LogLevel;
import com.google.common.collect.Lists;

public class ProxyHelper {
	public static enum ProxyType {
		automatic, os, firefox, none
	}
	protected static final Logger logger = LogManager.getLogger(ProxyHelper.class);

	static {
		try {
			com.btr.proxy.util.Logger.setBackend(new LogBackEnd() {

				@Override
				public void log(Class<?> paramClass, LogLevel paramLogLevel, String paramString, Object... paramArrayOfObject) {
					logger.info(paramString.replaceAll("\\{\\d\\}", "{}"), paramArrayOfObject);
				}

				@Override
				public boolean isLogginEnabled(LogLevel paramLogLevel) {
					return true;
				}
			});
		} catch (Exception e) {
			logger.error("Error initializing proxySearch", e);
		}
	}

	protected static String toHostUrl(String url) {
		int index = url.indexOf(':');
		if (index != -1) {
			index += 3;
			if (index < url.length()) {
				index = url.indexOf('/', index);
				if (index > -1) {
					return url.substring(0, index);
				}
			}
		}
		return null;
	}

	public static Proxy getProxy(String url, ProxyType proxyType) {
		switch (proxyType) {
			case os :
			case firefox :
			case automatic :
				List<Proxy> proxies = getProxies(url, proxyType);
				if (proxies != null && proxies.size() > 0) {
					return proxies.get(0);
				}
				break;
			default :
				break;
		}
		return Proxy.NO_PROXY;
	}

	protected static List<Proxy> getProxies(String url, ProxyType proxyType) {
		try {
			String host = toHostUrl(url);
			if (host != null) {
				ProxySearch proxySearch = new ProxySearch();

				switch (proxyType) {
					case automatic :
						proxySearch.addStrategy(Strategy.OS_DEFAULT); // results in IE too
						proxySearch.addStrategy(Strategy.ENV_VAR);
						proxySearch.addStrategy(Strategy.FIREFOX);
						proxySearch.addStrategy(Strategy.JAVA);
						break;
					case os :
						proxySearch.addStrategy(Strategy.OS_DEFAULT); // results in IE too
						break;
					case firefox :
						proxySearch.addStrategy(Strategy.FIREFOX);
						break;
					default :
						break;
				}

				proxySearch.setPacCacheSettings(0, 0); // Don't cache anything
				ProxySelector proxySelector = proxySearch.getProxySelector();

				if (proxySelector == null) {
					return Lists.newArrayList();
				}
				List<Proxy> proxies = proxySelector.select(new URI(host));
				if (proxies == null) {
					return Lists.newArrayList();
				}
				return proxies;
			}
		} catch (Exception e) {
			logger.error("Error detecting proxies for url {}", url, e);
		}
		return null;
	}
}
