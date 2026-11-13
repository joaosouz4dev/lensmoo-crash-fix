import brut.androlib.smali.SmaliBuilder;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.writer.builder.DexBuilder;
import com.android.tools.smali.dexlib2.writer.io.FileDataStore;
import java.nio.file.Files;
import java.nio.file.Path;

/** Assemble the repaired DEX only. The six other original DEX files are preserved. */
public class AssembleLensmooDex {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected smali_classes5 directory and output DEX");
        }
        var dex = new DexBuilder(new Opcodes(26));
        var assembler = new SmaliBuilder(26);
        try (var paths = Files.walk(Path.of(args[0]))) {
            for (var source : paths.filter(p -> p.toString().endsWith(".smali")).sorted().toList()) {
                if (!assembler.buildFile(source.toFile(), dex)) {
                    throw new IllegalStateException("Could not assemble " + source);
                }
            }
        }
        Path destination = Path.of(args[1]);
        Files.createDirectories(destination.toAbsolutePath().getParent());
        dex.writeTo(new FileDataStore(destination.toFile()));
        System.out.println("Repaired classes5.dex assembled successfully");
    }
}
