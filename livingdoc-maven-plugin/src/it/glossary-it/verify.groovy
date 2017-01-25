import java.io.*;

def destFile = "target" + File.separator + "generated-docs" + File.separator + "glossary.html"

return new File(basedir, destFile).exists();