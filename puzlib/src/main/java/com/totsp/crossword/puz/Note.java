package com.totsp.crossword.puz;

import java.io.Serializable;

public class Note implements Serializable {
    private String text;
    private String anagramSource;
    private String anagramSolution;

    public Note(String text, String anagramSource, String anagramSolution) {
        this.text = text;
        this.anagramSource = anagramSource;
        this.anagramSolution = anagramSolution;
    }

    public String getText() {
        return text;
    }

    public String getAnagramSource() {
        return anagramSource;
    }

    public String getAnagramSolution() {
        return anagramSolution;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setAnagramSource(String anagramSource) {
        this.anagramSource = anagramSource;
    }

    public void setAnagramSolution(String anagramSolution) {
        this.anagramSolution = anagramSolution;
    }

    public boolean isEmpty() {
        return (text == null || text.length() == 0) &&
               (anagramSource == null || anagramSource.length() == 0) &&
               (anagramSolution == null || anagramSolution.length() == 0);
    }
}
