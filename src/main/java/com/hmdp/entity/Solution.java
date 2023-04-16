/*
package com.hmdp.entity;

public class Solution {
    public static void main(String[] args) {
        reverseStr("abcdefg",2);
    }
    public static String reverseStr(String s, int k) {
        char[] ch = s.toCharArray();
        for(int i = 0;i < ch.length;i += 2 * k){
            int start = i;
            // 判断尾数够不够k个来取决end指针的位置
            int end = Math.min(ch.length - 1,start + k - 1);
            while(start < end){

                char temp = ch[start];
                ch[start] = ch[end];
                ch[end] = temp;

                start++;
                end--;
            }
        }
        System.out.println(ch);
        return new String(ch);
    }
}*/
