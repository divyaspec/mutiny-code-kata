package mutiny;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import static mutiny.io.readFile;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PasspolicyTest {
    public  int totalCount = 0;

    /**
     *
     * there is a problem with this test, its not shutting down after processing all bytes stream
     * eg., 3-4 j: tjjj
     * min no of j should be 3 and max no should be 4.
     */
    @Test
    public void shouldReadFileAndCountValidPasswordAsPerPolicy() throws ExecutionException, InterruptedException, IOException {
        File file = new File(getFilePath());
        Uni<byte[]> bytes = readFile(file);
        ByteArrayInputStream in = new ByteArrayInputStream(bytes.subscribe().asCompletionStage().get());
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(in));
        bufferedReader.lines().forEach(line -> {
            String[] splits = line.split(":");
            String policy = splits[0];
            String[] policySplits = policy.split(" ");
            Arrays.stream(policySplits).forEach(System.out::println);

            String s1 = policySplits[0];
            String[] counts = s1.split("-");
            totalCount = getValidPwdCounts(splits[1], policySplits[1].charAt(0), counts);
        });
        assertTrue(totalCount == 483);
    }

    private int getValidPwdCounts(String pwd, char ch, String[] counts) {
        int min = Integer.parseInt(counts[0]);
        int max = Integer.parseInt(counts[1]);
        //if s2 in the pwd. If in there then how many times from S1
        int count = getCount(ch, pwd, 0);
        if (count >= min && count <= max) {
            totalCount++;
            System.out.println("printing the total valid passwords = " + totalCount);
        }
        return totalCount;
    }

    private int getCount(char ch, String pwd, int count) {
        for (char c : pwd.toCharArray()) {
            if ( c == ch) {
                count++;
            }
        }
        return count;
    }

    public String getFilePath() {
        String home = System.getProperty("user.home");
        return home + File.separator + "Documents" + File.separator
                + "MyProject" + File.separator
                + "mutiny-code-kata" + File.separator
                + "src" + File.separator
                + "test" + File.separator
                + "resources" + File.separator
                + "Passpolicy.txt"
                ;
    }

}
