import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by d_d on 2/17/17.
 */
public class QryIopWindow extends QryIop {
    private int operatorDistance;


    private int match(int[] cur) {
        int min = Integer.MAX_VALUE;
        int min_index = 0;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < cur.length; i ++) {

            // Find the min value and the index with it, we need to advance the min against all locations in cur
            // later on if there's not a match.

            if (cur[i] < min) {
                min = cur[i];
                min_index = i;
            }

            // Find the max value in cur.

            if (cur[i] > max) {
                max = cur[i];
            }
        }

        // Check if it satisfied WINDOW's requirement.

        if (max - min >= this.operatorDistance) {
            return min_index;
        }
        return -1;
    }


    /**
     *  Evaluate the query operator; the result is an internal inverted
     *  list that may be accessed via the internal iterators.
     *  @throws IOException Error accessing the Lucene index.
     */
    protected void evaluate() throws IOException {

        //  Create an empty inverted list.  If there are no query arguments,
        //  that's the final result.

        this.invertedList = new InvList(this.getField());

        if (args.size() == 0){
            return;
        }

        // Each while loop search for next document that every argument
        // in WINDOW matches.

        while (this.docIteratorHasMatchAll(null)) {

            // Getting the matched document's id, since the while loop begin if all
            // arguments matched a document, so using the first argument's sufficient.

            QryIop matchDocId = (QryIop) this.args.get(0);

            int allLocs = -this.args.size();


            // Used to store current set of locations of all arguments in WINDOW.

            int[] cur = new int[this.args.size()];

            // Setting cur as the first location of every argument.

            for (int i = 0; i < this.args.size(); i++) {
                QryIop q_i = (QryIop) this.args.get(i);
                int docid = q_i.docIteratorGetMatch();

                // Getting the number of all locations of all argument.
                allLocs += q_i.docIteratorGetMatchPosting().tf;
                q_i.docIteratorAdvanceTo(docid);

                cur[i] = q_i.locIteratorGetMatch();

                // Advance locIteratorIndex everytime after using a location.

                q_i.locIteratorAdvance();
            }

            // Record how many loc's been used so far.

            int advanced = 0;

            // Save matched locations in positions, using matched location set's
            // last argument's location.

            List<Integer> positions = new ArrayList<>();

            // To cover the case that last time matched, and it's already reached at least
            // one argument's last location. Thus we don't need to check current set.

            boolean from_auto_break = false;

            // Iterate through all the locations.

            while (advanced < allLocs) {

                // Check if current set of locations matches requirement and store it.

                int unmatch_index = this.match(cur);

                // If matched

                if (unmatch_index == -1) {

                    // Whenever there's a match, we need to add the max value in cur to the positions.

                    int max = Integer.MIN_VALUE;
                    for (int i = 0; i < cur.length; i ++) {
                        if (cur[i] > max) {
                            max = cur[i];
                        }
                    }
                    positions.add(max);

                    // Advance every argument's location if matched.

                    for (int i = 0; i < cur.length; i++) {

                        QryIop q_i = (QryIop) this.args.get(i);

                        if (q_i.locIteratorHasMatch()) {
                            cur[i] = q_i.locIteratorGetMatch();
                            q_i.locIteratorAdvance();
                            advanced ++;

                        } else {

                            // If any argument's location is exhausted, break.

                            advanced = allLocs;
                            from_auto_break = true;
                            break;
                        }
                    }
                } else {

                    // If not match, get the smaller locations index in cur,
                    // and then advance the argument in this index.
                    int min_index = unmatch_index;
                    QryIop q_min = (QryIop) this.args.get(min_index);
                    if (q_min.locIteratorHasMatch()) {
                        cur[min_index] = q_min.locIteratorGetMatch();
                        q_min.locIteratorAdvance();
                        advanced ++;
                    } else {

                        // If the smaller index's argument's locations' exhausted, break
                        // and check the advanced new set of cur.

                        break;
                    }
                }
            }

            // We advanced some locIteratorIndex before breaking under some circumstances,
            // so we need to check match again.

            int unmatch_index = this.match(cur);
            if (unmatch_index == -1 && !from_auto_break) {

                // Whenever there's a match, we need to add the max value in cur to the positions.

                int max = Integer.MIN_VALUE;
                for (int i = 0; i < cur.length; i ++) {
                    if (cur[i] > max) {
                        max = cur[i];
                    }
                }
                positions.add(max);
            }

            // Append matched locations of this WINDOW operator to the invertedList of the operator.

            if (positions.size() != 0) {
                this.invertedList.appendPosting(matchDocId.docIteratorGetMatch(), positions);
            }

            // Advance past current docIteraorIndex so we can continue.
            matchDocId.docIteratorAdvancePast(matchDocId.docIteratorGetMatch());
        }
    }

    public QryIopWindow(int operatorDistance) {
        this.operatorDistance = operatorDistance;
    }
}
