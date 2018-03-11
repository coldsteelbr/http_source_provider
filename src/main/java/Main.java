import ru.romanbrazhnikov.sourceprovider.HttpMethods;
import ru.romanbrazhnikov.sourceprovider.HttpSourceProvider;

public class Main {
    public static void main(String[] arg){
        HttpSourceProvider provider = new HttpSourceProvider();
        provider.setBaseUrl("http://reforum.ru/comm");
        provider.setUrlDelimiter("?");
        provider.setQueryParamString("comm%5Btype%5D=sell&order=price_value_rur&order_direct=asc&page=2");
        provider.setHttpMethod(HttpMethods.GET); // default
        provider.requestSource().subscribe((s, throwable) -> {
            System.out.println(s);
        });

    }
}
