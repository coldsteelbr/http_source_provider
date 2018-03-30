package ru.romanbrazhnikov.sourceprovider;

import io.reactivex.Single;
import org.apache.commons.lang3.StringEscapeUtils;
import ru.romanbrazhnikov.sourceprovider.cookies.Cookie;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class HttpSourceProvider {
    static {
        System.setProperty("https.protocols", "TLSv1,TLSv1.1,TLSv1.2");
    }

    // TIME OUT
    private int mReadTimeOutMs = 3000;
    private int mConnectTimeOutMs = 3000;

    public void setReadTimeOutMs(int readTimeOutMs) {
        mReadTimeOutMs = readTimeOutMs;
    }

    public void setConnectTimeOutMs(int connectTimeOutMs) {
        mConnectTimeOutMs = connectTimeOutMs;
    }

    // UnEscape \\uXXXX string to normal chars
    boolean mDoUnEscape = false;

    private String mBaseUrl = "";
    private String mUrlDelimiter = "";

    // PROXY
    private boolean mUseProxy = false;
    private Proxy mProxy;

    // Cookies
    private List<String> mCookiesToRequest;
    private List<String> mCookiesFromResponse;

    // Headers
    private Map<String, String> mHeaders;

    private String mSourceEncoding = "utf8";
    private HttpMethods mHttpMethod = HttpMethods.GET;
    private String mQueryParamString;

    public void setBaseUrl(String baseUrl) {
        mBaseUrl = baseUrl;
    }

    public void setUrlDelimiter(String urlDelimiter) {
        mUrlDelimiter = urlDelimiter;
    }

    public void setSourceEncoding(String sourceEncoding) {
        switch (sourceEncoding.toLowerCase().replaceAll("\\s", "")) {
            case "utf-8":
            case "utf8":
                mSourceEncoding = "UTF-8";
                break;
            case "windows-1251":
            case "windows1251":
            case "cp-1251":
            case "cp1251":
            case "1251":
                mSourceEncoding = "Windows-1251";
                break;
            default:
                mSourceEncoding = sourceEncoding;
        }

    }

    public void setHttpMethod(HttpMethods httpMethod) {
        mHttpMethod = httpMethod;
    }

    public void setQueryParamString(String queryParamString) {
        mQueryParamString = queryParamString;
    }

    /// NEW LOGIC's METHODS

    private URL makeUrl() {
        URL url = null;
        try {
            //System.out.print("Forming URL: ");
            switch (mHttpMethod) {
                case GET:
                    String urlString = mBaseUrl + (mQueryParamString != null ? mUrlDelimiter + mQueryParamString : "");
                    url = new URL(urlString);
                    System.out.println("GET: " + urlString);
                    break;
                case POST:
                    url = new URL(mBaseUrl);
                    System.out.println("POST: " + mBaseUrl + "\nParams: " + mQueryParamString);
                    break;
            }
            //System.out.printf("%s://%s%s%s%s\n", url.getProtocol(), url.getHost(), url.getPath(), mUrlDelimiter, url.getQuery());
            return url;
        } catch (MalformedURLException e) {
            throw new TerminateHttpException("BAD URL. TERMINATE");
        }
    }

    private HttpURLConnection makeConnection(URL url, Proxy proxy) {
        HttpURLConnection connection;
        try {
            //System.out.println("Opening connection");

            if (proxy != null) {
                connection = (HttpURLConnection) url.openConnection(proxy);
            } else {
                connection = (HttpURLConnection) url.openConnection();
            }
            connection.setReadTimeout(mReadTimeOutMs);
            connection.setConnectTimeout(mConnectTimeOutMs);
            addHeadersIfAny(connection);
            addCookiesIfAny(connection);

            // IF POST
            if (mHttpMethod == HttpMethods.POST) {
                connection.setDoOutput(true);// Triggers POST.

                if (mHeaders == null) {
                    connection.setRequestProperty("User-Agent", "");
                    connection.setRequestProperty("Accept", "application/json, text/plain, */*");
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=" + StandardCharsets.UTF_8.displayName());
                }

                connection.setInstanceFollowRedirects(false);
            }

            return connection;

        } catch (IOException e) {
            throw new TerminateHttpException("Can't open Connection. Terminate");
        }
    }

    private int getResponseCode(HttpURLConnection connection) {
        int responseCode;
        try {
            //System.out.print("Getting Response code: ");
            responseCode = connection.getResponseCode();
            //System.out.println(responseCode);
            return responseCode;
        } catch (IOException e) {
            connection.disconnect();
            throw new TryAgainHttpException("Can't get response code. Try again\n" + e.getMessage());
        }
    }

    private InputStream getInputStream(HttpURLConnection connection) {
        InputStream in;
        try {
            //System.out.println("IN: sending request");
            in = connection.getInputStream();
            return in;
        } catch (IOException e) {
            connection.disconnect();
            throw new TryAgainHttpException("Can't get response code. Try again\n" + e.getMessage());
        }
    }

    private String getResponse(HttpURLConnection connection, InputStream in) {
        BufferedReader bReader;

        // reading response body according to the encoding
        if ("gzip".equals(connection.getContentEncoding())) {
            //System.out.println("Getting GZIP Input Stream");
            try {
                bReader = new BufferedReader(new InputStreamReader(new GZIPInputStream(in), mSourceEncoding));
            } catch (IOException e) {
                connection.disconnect();
                throw new TryAgainHttpException("CAN'T READ GZIP STREAM, try again\n" + e.getMessage());
            }
        } else {
            try {
                //System.out.println("Getting Input Stream Reader");
                bReader = new BufferedReader(new InputStreamReader(in, mSourceEncoding));
            } catch (UnsupportedEncodingException e) {
                connection.disconnect();
                throw new TerminateHttpException("ENCODING IS NOT SUPPORTED. TERMINATE\n" + e.getMessage());
            }
        }

        String response;
        String currentString;
        StringBuilder httpResponseStringBuilder = new StringBuilder();
        // building response string

        try {
            //System.out.println("Actual reading from input stream");
            while ((currentString = bReader.readLine()) != null) {
                httpResponseStringBuilder.append(currentString).append("\n");
            }

            bReader.close();

            response = httpResponseStringBuilder.toString();
            return mDoUnEscape ?
                    StringEscapeUtils.unescapeJava(response) :
                    response;

        } catch (IOException e) {
            connection.disconnect();
            throw new TryAgainHttpException("BUFFERED READER ERROR, try again");
        }
    }

    private void writeToConnection(HttpURLConnection connection) {
        if (mHttpMethod == HttpMethods.POST) {
            // Sending POST & Headers
            OutputStream outputStream;
            try {
                outputStream = connection.getOutputStream();
                DataOutputStream out;
                byte[] postData = mQueryParamString.trim().getBytes(StandardCharsets.UTF_8);
                out = new DataOutputStream(outputStream);
                // writing POST-data
                out.write(postData);
                out.flush();
                out.close();
            } catch (IOException e) {
                throw new TryAgainHttpException("Can't write output data. Try again");
            }
        }
    }

    /** MAIN METHOD */
    public Single<String> requestSource() {

        return Single.create(emitter -> {

            //System.out.println("Proxy: " + mProxy.address());

            try {
                // URL
                URL url = makeUrl();

                // CONNECTION
                HttpURLConnection connection = makeConnection(url, mProxy);

                // RESPONSE CODE
                int responseCode = getResponseCode(connection);

                // WRITING TO THE CONNECTION IF POST TODO: TEST THIS PART
                if (mHttpMethod == HttpMethods.POST) {
                    writeToConnection(connection);
                }

                // BAD RESPONSE CODE
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    emitter.onError(new TryAgainHttpException("Response code is not okay: " + responseCode));
                }

                // READING INPUT
                InputStream in = getInputStream(connection);

                // ACTUAL READING (BUFFERED READER)
                String response = getResponse(connection, in);


                emitter.onSuccess(response);
            }catch (Exception e){
                emitter.onError(e);
            }
/////////////////////

        }); // Single.create()...
    }

    public void setProxy(Proxy proxy){
        mProxy = proxy;
    }

    public void setHeaders(Map<String, String> headers) {
        mHeaders = headers;
    }

    private void addHeadersIfAny(HttpURLConnection httpConnection) {
        if (mHeaders != null) {
            for (Map.Entry<String, String> currentHeader : mHeaders.entrySet()) {
                httpConnection.setRequestProperty(currentHeader.getKey(), currentHeader.getValue());
            }
        }
    }

    private void addCookiesIfAny(HttpURLConnection httpConnection) {
        // Adding cookies if any
        if (mCookiesToRequest != null) {
            for (String currentCookie : mCookiesToRequest) {
                httpConnection.setRequestProperty("Cookie", currentCookie);
            }
        }
    }

    public void setCookiesHeadersToRequest(List<String> cookiesToRequest) {
        mCookiesToRequest = cookiesToRequest;
    }

    public void setCustomCookies(List<Cookie> cookieList) {
        // lazy initialization
        if (mCookiesToRequest == null) {
            mCookiesToRequest = new ArrayList<>();
        }

        // setting cookies
        for (Cookie currentCookie : cookieList) {
            mCookiesToRequest.add(currentCookie.getHeader());
        }

    }

    public List<String> getCookieHeadersFromResponse() {
        return mCookiesFromResponse;
    }
}
