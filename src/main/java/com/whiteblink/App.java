package com.whiteblink;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.whiteblink.model.GatewayResponse;
import lombok.SneakyThrows;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUpload;
import org.apache.commons.fileupload.RequestContext;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;

public class App implements RequestHandler<APIGatewayProxyRequestEvent, GatewayResponse> {

    @SneakyThrows
    @Override
    public GatewayResponse handleRequest(APIGatewayProxyRequestEvent input, Context context) {

        GatewayResponse responseEvent = new GatewayResponse();
        HashMap<String, String> hashMap = new HashMap<>();

        String content = getJsonFromFile(input);
        System.out.println(content);
        DataService service = new DataService();
        //Fetching the bytes of the received JSON body
        String mailContent = service.convertJSONToMailContent(content);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("summary",mailContent);
        responseEvent.setBody(jsonObject.toString());
        hashMap.put("content-type", "application/json");
        responseEvent.setHeaders(hashMap);
        responseEvent.setStatusCode(200);
        return responseEvent;

    }
    public String getJsonFromFile(APIGatewayProxyRequestEvent request){
        System.out.println("request headers "+request.getHeaders().get("content-type"));
        String content  = "";
        try {
            byte[] decodedBytes = java.util.Base64.getDecoder().decode(request.getBody().getBytes());
            InputStream decodedStream = new ByteArrayInputStream(decodedBytes);

            DiskFileItemFactory factory = new DiskFileItemFactory();
            FileUpload fileUpload = new FileUpload(factory);

            FileItemIterator iter = fileUpload.getItemIterator(new RequestContext() {
                @Override
                public InputStream getInputStream() {
                    return decodedStream;
                }

                @Override
                public String getContentType() {
                    return request.getHeaders().get("content-type");
                }

                @Override
                public String getCharacterEncoding() {
                    return "UTF-8";
                }

                @Override
                public int getContentLength() {
                    return -1; // Length is not available in Lambda from API Gateway
                }
            });

            while (iter.hasNext()) {
                FileItemStream item = iter.next();
                String fieldName = item.getFieldName();
                InputStream stream = item.openStream();
                if (!item.isFormField() && fieldName.equals("file")) {
                    content = new String(stream.readAllBytes());
                    System.out.println("File field " + fieldName + " with file name " + item.getName() + " detected.");
                } else {
                    System.out.println("Form field " + fieldName + " with value " + item.getName() + " detected.");
                }
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }
        return content;
    }
}

