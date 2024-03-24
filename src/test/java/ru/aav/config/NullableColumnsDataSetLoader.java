package ru.aav.config;

import com.github.springtestdbunit.dataset.FlatXmlDataSetLoader;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ReplacementDataSet;
import org.dbunit.dataset.csv.CsvDataSet;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.springframework.core.io.Resource;
import org.testcontainers.shaded.org.apache.commons.io.FilenameUtils;

import java.io.InputStream;
import java.util.Objects;

public class NullableColumnsDataSetLoader extends FlatXmlDataSetLoader {

    @Override
    public IDataSet createDataSet(Resource resource) throws Exception {
        IDataSet dataSet = createDataSetFromResource(resource);
        return createReplacementDataSet(dataSet);
    }

    private IDataSet createDataSetFromResource(Resource resource) throws Exception {
        String extension = FilenameUtils.getExtension(resource.getFilename());
        if (Objects.equals(extension, "xml")) {
            FlatXmlDataSetBuilder builder = new FlatXmlDataSetBuilder();
            builder.setColumnSensing(true);
            try (InputStream inputStream = resource.getInputStream()) {
                return createReplacementDataSet(builder.build(inputStream));
            }
        } else {
            return new CsvDataSet(resource.getFile());
        }
    }

    private ReplacementDataSet createReplacementDataSet(IDataSet dataSet) {
        ReplacementDataSet replacementDataSet = new ReplacementDataSet(dataSet);

        replacementDataSet.addReplacementObject("[null]", null);

        return replacementDataSet;
    }
}
