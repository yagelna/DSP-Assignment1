package bgu.ds.manager;

import bgu.ds.common.AWS;

public class Main {
    final static AWS aws = AWS.getInstance();
    public static void main(String[] args) {
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
