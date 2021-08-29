package com.zf1976.ddns.verticle.handler.spi;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

/**
 * @author mac
 * 2021/8/26 星期四 9:51 下午
 */
public interface WebhookHandler<T> {

    Future<HttpResponse<Buffer>> send(T t);

    void initClient(WebClient client);

}