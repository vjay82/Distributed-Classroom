package de.thickClient.browser;

import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.btr.proxy.search.ProxySearch;
import com.btr.proxy.search.ProxySearch.Strategy;
import com.btr.proxy.util.Logger.LogBackEnd;
import com.btr.proxy.util.Logger.LogLevel;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;

import de.thickClient.config.ThickClientConfiguration.ProxyType;
import de.thickClient.eventBus.EventBus;
import de.thickClient.eventBus.EventBus.EventListener;
import de.thickClient.eventBus.EventBus.ExecutionContext;
import de.thickClient.eventBus.events.Event;
import de.thickClient.eventBus.events.ProxyTypeChangedEvent;

public class ProxyHelper {
	protected static final Logger logger = LogManager.getLogger(ProxyHelper.class);
	protected static ProxyType proxyType;// ThickClient.getThickClientConfiguration().getProxyType();

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

	public static void createEventListener() {
		EventBus.get().addEventListener(ProxyTypeChangedEvent.class, ExecutionContext.IMMEDIATE, new EventListener() {

			@Override
			public void onThickClientEvent(Event event) {
				proxyType = ((ProxyTypeChangedEvent) event).getProxyType();
				proxyCache.invalidateAll();
			}
		});
	}

	protected static LoadingCache<String, List<Proxy>> proxyCache = CacheBuilder.newBuilder().concurrencyLevel(1).expireAfterAccess(10, TimeUnit.MINUTES).build(new CacheLoader<String, List<Proxy>>() {
		@Override
		public List<Proxy> load(String url) throws Exception {

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
			List<Proxy> proxies = proxySelector.select(new URI(url));
			if (proxies == null) {
				return Lists.newArrayList();
			}
			return proxies;
		}
	});

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

	public static void removeDetectedProxyFromCache(String url) {
		logger.info("Resetting detected proxies for {}", url);
		String host = toHostUrl(url);
		if (host != null) {
			proxyCache.invalidate(host);
		}
	}

	public static Proxy getProxy(String url) {
		switch (proxyType) {
			case os :
			case firefox :
			case automatic :
				List<Proxy> proxies = getProxies(url);
				if (proxies != null && proxies.size() > 0) {
					return proxies.get(0);
				}
				break;
			default :
				break;
		}
		return Proxy.NO_PROXY;
	}

	public static List<Proxy> getProxies(String url) {
		try {
			String host = toHostUrl(url);
			if (host != null) {
				return proxyCache.get(host);
			}
		} catch (ExecutionException e) {
			logger.error("Error detecting proxies for url {}", url, e);
		}
		return null;
	}
}
