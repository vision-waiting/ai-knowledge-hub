package com.example.knowledge.websource;
import org.junit.jupiter.api.Test;
import com.example.knowledge.common.BusinessException;
import static org.junit.jupiter.api.Assertions.*;
class PublicPageFetcherTest {
 @Test @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="KB_WEB_SMOKE", matches="true")
 void fetchesPublicDocumentationWithRealCurl(){
  var page=new PublicPageFetcher().fetch("https://jsoup.org/cookbook/input/parse-document-from-string");
  assertTrue(page.text().contains("parse"));assertFalse(page.title().isBlank());
 }
 @Test void rejectsLocalCredentialsAndNonHttpsBeforeNetwork(){
  var fetcher=new PublicPageFetcher();
  for(String url:java.util.List.of("file:///etc/passwd","http://example.com","https://127.0.0.1/x","https://[::1]/","https://user:pass@example.com","https://169.254.169.254/","https://example.com:8443/"))
   assertThrows(BusinessException.class,()->fetcher.fetch(url),url);
 }
}
