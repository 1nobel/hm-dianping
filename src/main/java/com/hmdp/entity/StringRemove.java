package com.hmdp.entity;

public class StringRemove {
    public static void main(String[] args) {
        String s = " Hello world  abc";

        StringBuilder sb = new StringBuilder();

        int start = 0;
        int end = s.length() - 1;

        while(s.charAt(start) == ' ') start++;
        while(s.charAt(end) == ' ') end--;

        while(start <= end){
            char c = s.charAt(start);
            if( c != ' ' || sb.charAt(sb.length() - 1) != ' '){
                sb.append(c);
            }
            start++;
        }

        System.out.println(sb.toString());
    }
}
