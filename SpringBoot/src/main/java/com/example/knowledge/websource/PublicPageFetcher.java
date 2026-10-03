package com.example.knowledge.websource;

import com.example.knowledge.common.*;
import org.springframework.stereotype.Component;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** curl pins the validated IP using --resolve; redirects are validated separately. No shell executes. */
@Component public class PublicPageFetcher {
    public record Page(String url,String title,String text){}
    @org.springframework.beans.factory.annotation.Value("${knowledge.web.curl-command:curl}") private String curl = "curl";
    public Page fetch(String url) {
        Path body=null,headers=null,errors=null;
        try {
            URI uri=URI.create(url);
            for(int hop=0;hop<4;hop++){
                if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getRawUserInfo()!=null||(uri.getPort()!=-1&&uri.getPort()!=443)||uri.toString().length()>2000)
                    throw bad("只支持无账号信息的公开 HTTPS 网页（443 端口）");
                var addresses=InetAddress.getAllByName(uri.getHost());
                for(var address:addresses)if(!publicAddress(address))throw bad("网页地址不能指向本机、内网或保留地址");
                String ip=addresses[0].getHostAddress(); if(ip.contains(":"))ip="["+ip+"]";
                body=Files.createTempFile("kb-web-",".html"); headers=Files.createTempFile("kb-web-",".headers");errors=Files.createTempFile("kb-web-",".errors");
                var process=new ProcessBuilder(curl,"-q","--silent","--show-error","--fail","--proto","=https","--noproxy","*",
                        "--connect-timeout","5","--max-time","20","--max-filesize","2097152","--resolve",uri.getHost()+":443:"+ip,
                        "--header","Accept-Encoding: identity","--output",body.toString(),"--dump-header",headers.toString(),"--url",uri.toString())
                        .redirectErrorStream(true).redirectOutput(errors.toFile()).start();
                try {
                    if(!process.waitFor(25,TimeUnit.SECONDS)){process.destroyForcibly();throw bad("网页读取超时");}
                    if(process.exitValue()!=0)throw bad("网页读取失败，可能不可访问、超过 2MB 或返回了错误状态");
                } finally { if(process.isAlive())process.destroyForcibly(); }
                String header=Files.readString(headers);var location=java.util.regex.Pattern.compile("(?im)^location:\\s*(.+)$").matcher(header);
                if(location.find()){
                    uri=uri.resolve(location.group(1).trim());Files.deleteIfExists(body);Files.deleteIfExists(headers);Files.deleteIfExists(errors);continue;
                }
                if(!header.toLowerCase(Locale.ROOT).contains("text/html")&&!header.toLowerCase(Locale.ROOT).contains("application/xhtml+xml"))throw bad("仅支持 HTML 网页");
                if(Files.size(body)>2097152)throw bad("网页超过 2MB 限制");
                var html=org.jsoup.Jsoup.parse(body.toFile(),null,uri.toString());
                html.select("script,style,nav,header,footer,aside,noscript,form").remove();
                var main=html.selectFirst("main,article");String text=(main==null?html.body():main).wholeText().trim();
                if(text.isBlank())throw bad("网页没有可提取正文；暂不支持需要登录或 JavaScript 渲染的页面");
                if(text.length()>200000)throw bad("网页正文超过 200,000 字限制");
                return new Page(uri.toString(),html.title().isBlank()?uri.getHost():html.title(),text);
            }
            throw bad("网页重定向次数过多");
        } catch(BusinessException e){throw e;}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw bad("网页读取已取消");}
        catch(Exception e){throw bad("网页读取失败，请检查 URL、网络和 curl 安装");}
        finally {for(Path path:new Path[]{body,headers,errors})if(path!=null)try{Files.deleteIfExists(path);}catch(java.io.IOException ignored){}}
    }
    static boolean publicAddress(InetAddress a){
        if(a.isAnyLocalAddress()||a.isLoopbackAddress()||a.isLinkLocalAddress()||a.isSiteLocalAddress()||a.isMulticastAddress())return false;
        byte[] b=a.getAddress();int x=Byte.toUnsignedInt(b[0]),y=Byte.toUnsignedInt(b[1]);
        if(b.length==16)return (x&0xe0)==0x20 && !(x==0x20&&y==0x01&&b[2]==0x0d&&b[3]==(byte)0xb8);
        return x!=0&&x!=10&&x!=127&&x<224&&!(x==100&&y>=64&&y<=127)&&!(x==169&&y==254)&&!(x==172&&y>=16&&y<=31)
                &&!(x==192&&(y==0||y==168))&&!(x==198&&(y==18||y==19||y==51))&&!(x==203&&y==0);
    }
    private BusinessException bad(String message){return new BusinessException(ErrorCode.BAD_REQUEST,message);}
}
