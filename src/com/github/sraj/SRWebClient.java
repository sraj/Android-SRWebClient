package com.github.sraj;

import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SRWebClient {

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
        } catch(MalformedURLException e) {
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

    public void cancel() {
        httpOperation.shutdown();
    }

    public SRWebClient headers(HashMap<String, String> headers) {
        if (headers != null && headers.size() > 0) {
            this.httpHeaders = headers;
        }
        return this;
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
                } catch (Exception e) {
                    Log.d(LOG_TAG, "Malformed URL");
                }
            } else {
                try {
                    postData = build(data).getBytes("UTF-8");
                } catch (Exception e) {
                    Log.d(LOG_TAG, "Malformed post data");
                }
            }
        }
        return this;
    }

    public SRWebClient data(byte[] uploadBytes, String fieldName, HashMap data) {
        ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
        try {
            if (uploadBytes != null && uploadBytes.length > 0 && httpMethod.equals(HttpMethod.POST)) {
                String boundMark = String.format("------WebKitFormBoundary%s", Long.toString(System.currentTimeMillis()));
                StringBuilder postBuild = new StringBuilder();
                if (httpHeaders == null) {
                    httpHeaders = new HashMap<String, String>();
                }
                httpHeaders.put("Connection", "Keep-Alive");
                httpHeaders.put("Content-Type", String.format("multipart/form-data; boundary=%s", boundMark));
                if (data != null && data.size() > 0) {
                    postBuild.append(String.format("--%s\r\n", boundMark));
                    List<String> mapKeys = new ArrayList<String>(data.keySet());
                    for (String key : mapKeys) {
                        postBuild.append(String.format("--%s\r\n", boundMark));
                        postBuild.append(String.format("Content-Disposition: form-data; name=\"%s\"\r\n\r\n", key));
                        postBuild.append(String.format("%s\r\n", String.valueOf(data.get(key))));
                    }
                }
                postBuild.append(String.format("--%s\r\n", boundMark));
                postBuild.append(String.format("Content-Disposition: form-data; name=\"%s\"; filename=\"%s.jpg\"\r\n",
                        fieldName,
                        Long.toString(System.currentTimeMillis() / 1000L)));
                postBuild.append("Content-Type: image/jpeg\r\n\r\n");
                byteOutput.write(new String(postBuild).getBytes("UTF-8"));
                byteOutput.write(uploadBytes);
                postBuild = new StringBuilder();
                postBuild.append("\r\n");
                postBuild.append(String.format("\r\n--%s--\r\n", boundMark));
                byteOutput.write(new String(postBuild).getBytes("UTF-8"));
                postData = byteOutput.toByteArray();
            }
        } catch (Exception e) {
            Log.d(LOG_TAG, "Malformed post data");
        }
        return this;
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
                    httpConn.setReadTimeout(timeoutInterval);
                    httpConn.setUseCaches(false);

                    if (httpMethod.equals(HttpMethod.POST) && postData != null) {
                        httpConn.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                    }

                    if (httpHeaders != null && !httpHeaders.isEmpty()) {
                        for (String key : httpHeaders.keySet()) {
                            httpConn.setRequestProperty(key, httpHeaders.get(key));
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
                                String contentType = httpConn.getHeaderField("Content-Type");
                                Message result = success.obtainMessage();
                                if (contentType != null && contentType.contains("application/json")) {
                                    try {
                                        result.obj = new JSONObject(response);
                                    } catch (JSONException joe) {
                                        try {
                                            result.obj = new JSONArray(response);
                                        } catch (JSONException jae) {
                                            throw jae;
                                        }
                                    }
                                } else {
                                    result.obj = response;
                                }
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
        httpOperation.shutdown();
        return this;
    }
}