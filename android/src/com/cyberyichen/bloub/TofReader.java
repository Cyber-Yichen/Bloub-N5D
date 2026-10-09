package com.cyberyichen.bloub;
import java.io.*;
/** Fixed read-only Magisk helper. EOF, parent death, or shutdown ends polling. */
public final class TofReader {
    private static volatile boolean running=true;
    public static void main(String[] args){
        if(args.length!=1||!args[0].matches("[0-9]+"))return;
        final File parent=new File("/proc/"+args[0]);
        Thread eof=new Thread(()->{try{while(System.in.read()!=-1){} }catch(IOException ignored){}running=false;});eof.setDaemon(true);eof.start();
        while(running&&parent.exists()){
            try(BufferedReader in=new BufferedReader(new FileReader("/sys/class/misc/stmvl53l4cd/tof_distance"))){String line=in.readLine();System.out.println(line==null?"invalid":line);System.out.flush();}
            catch(IOException e){System.out.println("invalid");System.out.flush();}
            try{Thread.sleep(1000);}catch(InterruptedException e){break;}
        }
    }
}
