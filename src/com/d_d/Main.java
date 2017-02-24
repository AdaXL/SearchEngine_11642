package com.d_d;

import java.io.IOException;

public class Main {

    public static void main(String[] args) throws IOException {
        // write your code here
        QryParser qryParser = new QryParser();
        Qry qry = qryParser.getQuery("#OR (#NEAR/3 (apple pie) bananas)");
        //ScoreList r = QryEval.processQuery(qry, )
        System.out.print(qry.toString());
    }
}
