import com.example.voicesemantic.PcmWavWriter;
import java.io.File;
import java.nio.file.Files;

public final class PcmWavWriterTest {
    public static void main(String[] args) throws Exception {
        File tmp=File.createTempFile("speech-", ".wav");
        try {
            try(PcmWavWriter wav=new PcmWavWriter(tmp,48000,1)){
                wav.append(new short[]{0,32767,-32768},3);
            }
            byte[] b=Files.readAllBytes(tmp.toPath());
            if(b.length!=50)throw new AssertionError("Invalid size: "+b.length);
            if(b[0]!='R'||b[1]!='I'||b[2]!='F'||b[3]!='F')
                throw new AssertionError("Invalid WAV header");
            if(b[40]!=6 || b[41]!=0 || b[42]!=0 || b[43]!=0)
                throw new AssertionError("Invalid payload length");
            if((b[46]&255)!=255 || (b[47]&255)!=127)
                throw new AssertionError("Invalid PCM endian format");
            System.out.println("PcmWavWriterTest PASSED");
        }finally{tmp.delete();}
    }
}
