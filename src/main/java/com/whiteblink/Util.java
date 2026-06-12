package com.whiteblink;

public class Util {
    public static String escape(String s) {
        s=s.replace("\n","<br></br>");
        s=s.replace("\t","<br></br>");
        s=s.replace("&","---AMP---");
        s=s.replace("<br></br>","---BREAK---");
        s=s.replace("<","&lt;");
        s=s.replace(">","&gt;");
        s=s.replace("#","&#35;");
        s=s.replace("\"","&quot;");
        s=s.replace("---AMP---","&amp;");
        s=s.replace("---BREAK---","<br></br>");
        return s;
    }
}
