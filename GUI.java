import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class GUI {
    private JTextField queryField;
    private JList<String> resultList;
    private DefaultListModel<String> listModel;
    private IndexAllFilesInDirectory indexer;
    private SearchIndexedDocs indexSearcher;
    private String index = "E:\\IR Project\\citeseer2_index"; //paperTitles_index";
    private int currentPage = 0;
    private final int resultsPerPage = 5;
    private int curOption = 0;
    private String searchField = "contents";
    private Boolean matchCase = false;

    public GUI() throws Exception {
        JFrame frame = new JFrame("Lucene Search");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        frame.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JCheckBox checkBoxPhraseGap = new JCheckBox("Phrase_");
        JCheckBox checkBoxPhraseGapKnown = new JCheckBox("Phrase?");
        JCheckBox checkBoxPhraseP = new JCheckBox("Phrase~");
        JCheckBox checkBoxPhraseWc = new JCheckBox("Phrase*");
        JCheckBox checkBoxText = new JCheckBox("Text");
        JCheckBox checkBoxTitle = new JCheckBox("Title");
        JCheckBox checkBoxCase = new JCheckBox("Match Case");

        queryField = new JTextField(20);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 15;
        frame.add(queryField, gbc);

        JButton searchButton = new JButton("Search");
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy = 1;
        frame.add(searchButton, gbc);

        JButton prevButton = new JButton("Previous");
        gbc.gridwidth = 1;
        gbc.gridx = 1;
        gbc.gridy = 1;
        frame.add(prevButton, gbc);

        JButton nextButton = new JButton("Next");
        gbc.gridwidth = 1;
        gbc.gridx = 2;
        gbc.gridy = 1;
        frame.add(nextButton, gbc);

        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy = 2;
        frame.add(checkBoxText, gbc); //Text checkbox

        gbc.gridwidth = 1;
        gbc.gridx = 1;
        gbc.gridy = 2;
        frame.add(checkBoxTitle, gbc); //Title checkbox

        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy = 3;
        frame.add(checkBoxPhraseP, gbc); //phrase~

        gbc.gridwidth = 1;
        gbc.gridx = 1;
        gbc.gridy = 3;
        frame.add(checkBoxPhraseWc, gbc); //phrase*

        gbc.gridwidth = 1;
        gbc.gridx = 2;
        gbc.gridy = 3;
        frame.add(checkBoxPhraseGap, gbc); //phrase/

        gbc.gridwidth = 1;
        gbc.gridx = 3;
        gbc.gridy = 3;
        frame.add(checkBoxPhraseGapKnown, gbc); //phrase?

        gbc.gridwidth = 1;
        gbc.gridx = 1;
        gbc.gridy = 4;
        frame.add(checkBoxCase, gbc); //Text checkbox

        listModel = new DefaultListModel<>();
        resultList = new JList<>(listModel);
        resultList.setVisibleRowCount(10);
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        resultList.setCellRenderer(new HtmlListCellRenderer());

        JScrollPane listScrollPane = new JScrollPane(resultList);
        listScrollPane.setPreferredSize(new Dimension(800, 200));

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 15;
        gbc.fill = GridBagConstraints.BOTH;
        frame.add(listScrollPane, gbc);

        searchButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    listModel.clear();
                    currentPage = 0;
                    indexSearcher = new SearchIndexedDocs(index);
                    indexSearcher.search(queryField.getText(), listModel, searchField, currentPage, resultsPerPage, curOption, matchCase);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        prevButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (currentPage > 0) {
                    currentPage--;
                    try {
                        listModel.clear();
                        indexSearcher.search(queryField.getText(), listModel, searchField, currentPage, resultsPerPage, curOption, matchCase);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        });

        nextButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                currentPage++;
                try {
                    listModel.clear();
                    indexSearcher.search(queryField.getText(), listModel, searchField, currentPage, resultsPerPage, curOption, matchCase);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        checkBoxPhraseP.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (checkBoxPhraseP.isSelected()) {
                    curOption = 1;
                    checkBoxPhraseWc.setSelected(false);
                    checkBoxPhraseGap.setSelected(false);
                    checkBoxPhraseGapKnown.setSelected(false);
                }
                if (!checkBoxPhraseP.isSelected() && !checkBoxPhraseWc.isSelected() && !checkBoxPhraseGap.isSelected() && !checkBoxPhraseGapKnown.isSelected()) {
                    curOption = 0;
                }
            }
        });

        checkBoxPhraseWc.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (checkBoxPhraseWc.isSelected()) {
                    curOption = 2;
                    checkBoxPhraseP.setSelected(false);
                    checkBoxPhraseGap.setSelected(false);
                    checkBoxPhraseGapKnown.setSelected(false);
                }
                if (!checkBoxPhraseP.isSelected() && !checkBoxPhraseWc.isSelected() && !checkBoxPhraseGap.isSelected() && !checkBoxPhraseGapKnown.isSelected()) {
                    curOption = 0;
                }
            }
        });

        checkBoxPhraseGap.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (checkBoxPhraseGap.isSelected()) {
                    curOption = 3;
                    checkBoxPhraseP.setSelected(false);
                    checkBoxPhraseWc.setSelected(false);
                    checkBoxPhraseGapKnown.setSelected(false);
                }
                if (!checkBoxPhraseP.isSelected() && !checkBoxPhraseWc.isSelected() && !checkBoxPhraseGap.isSelected() && !checkBoxPhraseGapKnown.isSelected()) {
                    curOption = 0;
                }
            }
        });

        checkBoxPhraseGapKnown.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (checkBoxPhraseGapKnown.isSelected()) {
                    curOption = 4;
                    checkBoxPhraseP.setSelected(false);
                    checkBoxPhraseWc.setSelected(false);
                    checkBoxPhraseGap.setSelected(false);
                }
                if (!checkBoxPhraseP.isSelected() && !checkBoxPhraseWc.isSelected() && !checkBoxPhraseGap.isSelected() && !checkBoxPhraseGapKnown.isSelected()) {
                    curOption = 0;
                }
            }
        });

        checkBoxText.setSelected(true); //default search contents
        checkBoxText.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (checkBoxText.isSelected()) {
                    searchField = "contents";
                    checkBoxTitle.setSelected(false);
                }
                if (!checkBoxText.isSelected() && !checkBoxTitle.isSelected()) {
                    checkBoxText.setSelected(true); //default to searching contents
                }
            }
        });

        checkBoxTitle.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (checkBoxTitle.isSelected()) {
                    searchField = "title";
                    checkBoxText.setSelected(false);
                }
                if (!checkBoxText.isSelected() && !checkBoxTitle.isSelected()) {
                    checkBoxText.setSelected(true);
                }
            }
        });

        checkBoxCase.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                matchCase = checkBoxCase.isSelected();
            }
        });

        checkBoxCase.setVisible(false); //case matching not too helpful for me

        frame.pack();
        frame.setVisible(true);
    }

    private static class HtmlListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            label.setText(value.toString());
            label.setOpaque(true);

            if (isSelected) {
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
            } else {
                label.setBackground(list.getBackground());
                label.setForeground(list.getForeground());
            }

            return label;
        }
    }

    public static void main(String[] args) throws Exception {
        new GUI();
    }
}