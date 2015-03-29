package com.github.sraj;

import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SRWebClient {

    public static enum HttpMethod { GET, POST }
    public int timeoutInterval = 30 * 1000; //Milliseconds

    private static final String LOG_TAG = SRWebClient.class.getSimpleName();
    private static final Map<HttpMethod,String> httpMethodStringMap = new HashMap<HttpMethod,String>();
    private HttpMethod httpMethod = HttpMethod.GET;
    private ExecutorService httpOperation;
    private byte[] postData;
    private URL httpURL;
    private HashMap<String, String> httpHeaders;

    static {
        httpMethodStringMap.put(HttpMethod.GET, "GET");
        httpMethodStringMap.put(HttpMethod.POST, "POST");
    }

    public SRWebClient(String url, HttpMethod method) {

        httpOperation = Executors.newSingleThreadExecutor();

        if (method != null) {
            httpMethod = method;
        }

        try {
            httpURL = new URL(url);
        } catch(Throwable t) {
            Log.d(LOG_TAG, "Malformed URL");
        }
    }

    public static SRWebClient GET(String url) {
        return new SRWebClient(url, HttpMethod.GET);
    }

    public static SRWebClient GET(String url, final Handler success, final Handler failure) {
        return SRWebClient.GET(url).send(success, failure);
    }

    public static SRWebClient POST(String url) {
        return new SRWebClient(url, HttpMethod.POST);
    }

    public static SRWebClient POST(String url, final Handler success, final Handler failure) {
        return SRWebClient.POST(url).send(success, failure);
    }

    private String build(HashMap data) throws Exception {
        ArrayList<String> dataList = new ArrayList<String>();
        List<String> mapKeys = new ArrayList<String>(data.keySet());
        for (String key : mapKeys) {
            dataList.add(String.format("%s=%s", key, Uri.encode(String.valueOf(data.get(key)))));
        }
        return TextUtils.join("&", dataList);
    }

    public SRWebClient data(HashMap data) {
        if (data != null && data.size() > 0) {
            if (httpMethod.equals(HttpMethod.GET)) {
                try {
                    httpURL = new URL(httpURL.toString() + "?" + build(data));
                } catch (Throwable t) {
                    Log.d(LOG_TAG, "Malformed URL");
                }
            } else {
                try {
                    postData = build(data).getBytes("UTF-8");
                } catch (Throwable t) {
                    Log.d(LOG_TAG, "Malformed post data");
                }
            }
        }
        return this;
    }

    public SRWebClient headers(HashMap<String, String> headers) {
        if (headers != null && headers.size() > 0) {
            this.httpHeaders = headers;
        }
        return this;
    }

    public void cancel() {
        httpOperation.shutdown();
    }

    public SRWebClient send(final Handler success, final Handler failure) {

        httpOperation.submit(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection httpConn = null;
                try {

                    httpConn = (HttpURLConnection) httpURL.openConnection();
                    httpConn.setRequestMethod(httpMethodStringMap.get(httpMethod));
                    httpConn.setConnectTimeout(timeoutInterval);
                    httpConn.setInstanceFollowRedirects(false);

                    if (httpHeaders != null && !httpHeaders.isEmpty()) {
                        for (String key : httpHeaders.keySet()) {
                            httpConn.addRequestProperty(key, httpHeaders.get(key));
                        }
                    }

                    if (httpMethod.equals(HttpMethod.POST) && postData != null) {
                        httpConn.setDoOutput(true);
                        httpConn.setRequestProperty("Content-Length", String.valueOf(postData.length));
                        httpConn.getOutputStream().write(postData);
                    }

                    httpConn.connect();

                    int status = httpConn.getResponseCode();
                    String response = "";
                    if(status >= HttpURLConnection.HTTP_OK && status < HttpURLConnection.HTTP_MULT_CHOICE) {
                        if(httpConn.getResponseMessage() != null) {
                            Scanner inStream = new Scanner(httpConn.getInputStream());
                            while (inStream.hasNextLine()) {
                                response += (inStream.nextLine());
                            }
                            inStream.close();
                            if(success != null) {
                                Message result = success.obtainMessage();
                                result.obj = response;
                                success.sendMessage(result);
                            }
                        }
                    } else {
                        if(failure != null) {
                            Message result = failure.obtainMessage();
                            result.obj = Integer.toString(status);
                            failure.sendMessage(result);
                        }
                    }
                } catch (Exception e) {
                    if(failure != null) {
                        Message result = failure.obtainMessage();
                        result.obj = Integer.toString(0);
                        failure.sendMessage(result);
                    }
                    e.printStackTrace();
                } finally {
                    if (httpConn != null) {
                        httpConn.disconnect();
                    }
                }
            }
        });
        httpOperation.shutdown()
        return this;
    }
}