package com.whiteblink;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

public class App2
{
    public static void main( String[] args )
    {
        DataService service = new DataService();
        try{
            OutputStream outputStream = new FileOutputStream("SO-202403-001730 (2) (1)_mail_content.txt");
            String content = new String(Files.readAllBytes(Paths.get("SO-202403-001730 (2) (1).json")));
            String mailContent = service.convertJSONToMailContent(content);
            System.out.println("mail content \n"+mailContent);
            outputStream.write(mailContent.getBytes());
        } catch (Exception var8) {
            var8.printStackTrace();
        }
    }
}