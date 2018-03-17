import ru.romanbrazhnikov.circular_queue.CircularQueue;
import ru.romanbrazhnikov.fileutils.FileUtils;
import ru.romanbrazhnikov.sourceprovider.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] arg) {
        CircularQueue<IpPort> proxyList = new CircularQueue<>();

        try {

            String proxyListString = FileUtils.readFromFileToString("proxy_list.txt");
            String[] splitByEOL = proxyListString.split("\\r?\\n|\\r");
            for (String currentIpPortString : splitByEOL) {
                proxyList.add(new IpPort(currentIpPortString));
            }

        } catch (IOException e) {
            System.err.println("Can't read proxy list file");
            return;
        }

        FinalBuffer<Boolean> isTrying = new FinalBuffer<>(true);
        int pageCount = 1000;
        HttpSourceProvider provider = new HttpSourceProvider();
        //provider.setBaseUrl("https://reforum.ru/flat");
        provider.setHttpMethod(HttpMethods.GET);
        provider.setBaseUrl("https://novosibirsk.irr.ru");
        provider.setUrlDelimiter("/");
        String paramString = "/real-estate/apartments-sale/secondary/page{[PAGE]}/";

        for (int i = 0; i < pageCount; i++) {
            String realParams = paramString.replace("{[PAGE]}", String.valueOf(i));
            provider.setQueryParamString(realParams);
            isTrying.value = true;
            while (isTrying.value && proxyList.getSize() > 0) {
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                IpPort currentProxyIp = proxyList.reuse();
                provider.setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(currentProxyIp.getIp(), currentProxyIp.getPort())));

                provider.requestSource().subscribe(s -> {
                    System.out.println("s = " + s);
                    isTrying.value = false;
                }, throwable -> {
                    isTrying.value = throwable instanceof TryAgainHttpException;
                    System.err.println(throwable.getMessage());
                });
            }
        }


    }
}
