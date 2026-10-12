package com.cyberyichen.bloub;
import java.security.SecureRandom;
import java.util.Locale;
final class AccessCode {
  static String validate(String value){if(value==null||!value.matches("[0-9]{6}"))throw new IllegalArgumentException("输入六位数字访问码");return value;}
  static String refresh(String previous){SecureRandom random=new SecureRandom();String value;do{value=String.format(Locale.US,"%06d",random.nextInt(1000000));}while(value.equals(previous));return value;}
}
