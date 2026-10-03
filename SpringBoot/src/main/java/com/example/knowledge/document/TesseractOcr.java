package com.example.knowledge.document;
import org.springframework.stereotype.Component;
import java.awt.image.BufferedImage;
@Component public class TesseractOcr {
    @org.springframework.beans.factory.annotation.Value("${knowledge.ocr.command:}") private String command="";
    @org.springframework.beans.factory.annotation.Value("${knowledge.ocr.languages:chi_sim+eng}") private String languages="chi_sim+eng";
    public boolean enabled(){return !command.isBlank();}
    public String recognize(BufferedImage page) throws java.io.IOException {
        if(!enabled())throw new java.io.IOException("未配置 OCR 引擎");
        var input=java.nio.file.Files.createTempFile("kb-ocr-",".png");var output=java.nio.file.Path.of(input+".result");var log=java.nio.file.Path.of(input+".log");
        Process process=null;
        try {
            javax.imageio.ImageIO.write(page,"png",input.toFile());
            process=new ProcessBuilder(command,input.toString(),output.toString(),"-l",languages,"--psm","3").redirectErrorStream(true).redirectOutput(log.toFile()).start();
            if(!process.waitFor(45,java.util.concurrent.TimeUnit.SECONDS)){process.destroyForcibly();throw new java.io.IOException("OCR 单页识别超时");}
            if(process.exitValue()!=0)throw new java.io.IOException("OCR 识别失败，请检查 Tesseract 路径和语言包 "+languages);
            var result=java.nio.file.Path.of(output+".txt");if(java.nio.file.Files.size(result)>2_000_000)throw new java.io.IOException("OCR 结果超出限制");
            return java.nio.file.Files.readString(result).trim();
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new java.io.IOException("OCR 已取消",e);}
        finally {if(process!=null&&process.isAlive())process.destroyForcibly();for(var p:java.util.List.of(input,java.nio.file.Path.of(output+".txt"),log))java.nio.file.Files.deleteIfExists(p);}
    }
}
