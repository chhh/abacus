package abacus.config;

import java.nio.file.Path;
import java.util.List;

/**
 * Parameters for adding MS1 quantitation information.
 * @author Dmitry Avtonomov
 */
public class MS1QuantOpts {
    protected Path filePath;
    protected boolean isHeaderPresent = true;
    protected String separator = "\t";

    protected String modSeqColName;
    protected int modSeqColIdx;
    protected List<String> quantColNames;
    protected List<Integer> quantColIdxs;
    protected String chargeColName;
    protected int chargeColIdx;

    public Path getFilePath() {
        return filePath;
    }

    public void setFilePath(Path filePath) {
        this.filePath = filePath;
    }

    public boolean isHeaderPresent() {
        return isHeaderPresent;
    }

    public void setIsHeaderPresent(boolean isHeaderPresent) {
        this.isHeaderPresent = isHeaderPresent;
    }

    public String getSeparator() {
        return separator;
    }

    public void setSeparator(String separator) {
        this.separator = separator;
    }

    public String getModSeqColName() {
        return modSeqColName;
    }

    public void setModSeqColName(String modSeqColName) {
        this.modSeqColName = modSeqColName;
    }

    public int getModSeqColIdx() {
        return modSeqColIdx;
    }

    public void setModSeqColIdx(int modSeqColIdx) {
        this.modSeqColIdx = modSeqColIdx;
    }

    public List<String> getQuantColNames() {
        return quantColNames;
    }

    public void setQuantColNames(List<String> quantColNames) {
        this.quantColNames = quantColNames;
    }

    public List<Integer> getQuantColIdxs() {
        return quantColIdxs;
    }

    public void setQuantColIdxs(List<Integer> quantColIdxs) {
        this.quantColIdxs = quantColIdxs;
    }

    public String getChargeColName() {
        return chargeColName;
    }

    public void setChargeColName(String chargeColName) {
        this.chargeColName = chargeColName;
    }

    public int getChargeColIdx() {
        return chargeColIdx;
    }

    public void setChargeColIdx(int chargeColIdx) {
        this.chargeColIdx = chargeColIdx;
    }
}
