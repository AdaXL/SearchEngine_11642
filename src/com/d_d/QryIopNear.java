package com.d_d;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by d_d on 2/2/17.
 */
public class QryIopNear extends QryIop {
    private int operatorDistance;

    private int match(int[] cur) {
        for (int i = 1; i < cur.length; i ++){
            if ( !(cur[i] - cur[ i - 1 ] <= this.operatorDistance && cur[i] - cur[i - 1] >= 0) ) {
                return i;
            }
        }
        return -1;
    }

    public static int getIndex(QryIop qry, int docid){
        for (int i = 0; i < qry.invertedList.df; i ++){
            if (qry.invertedList.postings.get(i).docid == docid) {
                return i;
            }
        }
        return -1;
    }

    protected void evaluate() throws IOException {
        this.invertedList = new InvList(this.getField());

        if (args.size() == 0){
            return;
        }


        while (this.docIteratorHasMatchAll(null)) {
            QryIop matchDocId = (QryIop) this.args.get(0);

            int allLocs = -this.args.size();

            int[] cur = new int[this.args.size()];

            for (int i = 0; i < this.args.size(); i++) {
                QryIop q_i = (QryIop) this.args.get(i);
                int docid = q_i.docIteratorGetMatch();
                allLocs += q_i.invertedList.getTf(getIndex(q_i, docid));
                q_i.docIteratorAdvanceTo(docid);
                cur[i] = q_i.locIteratorGetMatch();
                q_i.locIteratorAdvance();
            }

            int advanced = 0;
            List<Integer> positions = new ArrayList<>();
            boolean from_auto_break = false;
            while (advanced < allLocs) {
                int unmatch_index = this.match(cur);
                if (unmatch_index == -1) {
                    positions.add(cur[cur.length - 1]);
                    for (int i = 0; i < cur.length; i++) {
                        QryIop q_i = (QryIop) this.args.get(i);
                        if (q_i.locIteratorHasMatch()) {
                            cur[i] = q_i.locIteratorGetMatch();
                            q_i.locIteratorAdvance();
                            advanced ++;
                        } else {
                            advanced = allLocs;
                            from_auto_break = true;
                            break;
                        }
                    }
                } else {
                    int min_index = cur[unmatch_index] < cur[unmatch_index - 1] ? unmatch_index : unmatch_index - 1;
                    QryIop q_min = (QryIop) this.args.get(min_index);
                    if (q_min.locIteratorHasMatch()) {
                        cur[min_index] = q_min.locIteratorGetMatch();
                        q_min.locIteratorAdvance();
                        advanced ++;
                    } else {
                        break;
                    }
                }
            }
            int unmatch_index = this.match(cur);
            if (unmatch_index == -1 && !from_auto_break) {
                positions.add(cur[cur.length - 1]);
            }

            if (positions.size() != 0) {
                this.invertedList.appendPosting(matchDocId.docIteratorGetMatch(), positions);
            }

            matchDocId.docIteratorAdvancePast(matchDocId.docIteratorGetMatch());
        }
    }

    public QryIopNear(int operatorDistance) {
        this.operatorDistance = operatorDistance;
    }
}
