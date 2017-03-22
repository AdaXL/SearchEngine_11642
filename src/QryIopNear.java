import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by d_d on 2/2/17.
 */
public class QryIopNear extends QryIop {
    private int operatorDistance;


   /**
    *  For a set of current locations, Check if all the locations meet the
    *  distance requirements, it's pairwise.
    *  @param cur Current set of locations.
    *  @return -1 if match, unmatch index if not.
    */
    private int match(int[] cur) {
        for (int i = 1; i < cur.length; i ++){
            // Checking matching requirement.
            if ( !(cur[i] - cur[ i - 1 ] <= this.operatorDistance && cur[i] - cur[i - 1] >= 0) ) {
                return i;
            }
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
        // in NEAR matches.

        while (this.docIteratorHasMatchAll(null)) {

            // Getting the matched document's id, since the while loop begin if all
            // arguments matched a document, so using the first argument's sufficient.

            QryIop matchDocId = (QryIop) this.args.get(0);

            int allLocs = -this.args.size();


            // Used to store current set of locations of all arguments in NEAR.

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
                    positions.add(cur[cur.length - 1]);

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

                    int min_index = cur[unmatch_index] < cur[unmatch_index - 1] ? unmatch_index : unmatch_index - 1;
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
                positions.add(cur[cur.length - 1]);
            }

            // Append matched locations of this NEAR operator to the invertedList of the operator.

            if (positions.size() != 0) {
                this.invertedList.appendPosting(matchDocId.docIteratorGetMatch(), positions);
            }

            // Advance past current docIteraorIndex so we can continue.
            matchDocId.docIteratorAdvancePast(matchDocId.docIteratorGetMatch());
        }
    }


    // Every NEAR operator has a property of distance, initialize the QryIopNear
    // object using operatorDistance.

    public QryIopNear(int operatorDistance) {
        this.operatorDistance = operatorDistance;
    }
}
