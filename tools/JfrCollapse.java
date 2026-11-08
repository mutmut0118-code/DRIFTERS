// Fast reader for a Java Flight Recorder profile (tools/smoke.py "jfr" line): one line per distinct stack, "count thread;root;...;leaf".
// Usage: java tools/JfrCollapse.java <file.jfr> <out.col>, then python tools/jfr_query.py <out.col> [--children M | --callers M | --within M].
// (jfr print --json, which tools/jfrtop.py uses, took over ten minutes on a 60 s profile; this takes 2 s.)
import java.nio.file.*;
import java.util.*;
import jdk.jfr.consumer.*;

public class JfrCollapse {
	public static void main(String[] args) throws Exception {
		Map<String, Integer> counts = new HashMap<>();
		int n = 0;
		try (RecordingFile file = new RecordingFile(Path.of(args[0]))) {
			while (file.hasMoreEvents()) {
				RecordedEvent e = file.readEvent();
				if (!e.getEventType().getName().equals("jdk.ExecutionSample")) continue;
				RecordedThread th = e.getThread("sampledThread");
				String tn = th == null ? "?" : th.getJavaName();
				RecordedStackTrace st = e.getStackTrace();
				StringBuilder sb = new StringBuilder(tn);
				if (st != null) {
					List<RecordedFrame> fr = st.getFrames();
					for (int i = Math.min(fr.size(), 96) - 1; i >= 0; i--) {
						var m = fr.get(i).getMethod();
						String t = m.getType().getName();
						t = t.substring(t.lastIndexOf('.') + 1);
						sb.append(';').append(t).append('.').append(m.getName());
					}
				}
				counts.merge(sb.toString(), 1, Integer::sum);
				n++;
			}
		}
		try (var out = Files.newBufferedWriter(Path.of(args[1]))) {
			for (var en : counts.entrySet()) out.write(en.getValue() + " " + en.getKey() + "\n");
		}
		System.out.println(n + " samples");
	}
}
