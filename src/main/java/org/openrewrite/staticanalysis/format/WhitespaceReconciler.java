/*
 * Copyright 2026 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.staticanalysis.format;

import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.PrintOutputCapture;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.marker.Marker;
import org.openrewrite.marker.Markers;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.Space;

import java.util.*;
import java.util.function.Function;

/**
 * Puts the formatting of a formatted copy of a source file onto the original, so that the result prints as the
 * formatter wrote it but keeps the original's types and markers. The formatted copy is the formatter's output
 * parsed again, which lacks the types of the project's dependencies, since the parser that read it had no class
 * path.
 * <p>
 * The two trees are walked in the same order. Each tree of the original must be of the same kind as the tree at
 * the same place in the formatted copy, and takes from it:
 * <ul>
 *     <li>each {@link Space}, which holds whitespace and comments,</li>
 *     <li>the markers that stand for syntax rather than meaning, a semicolon or a trailing comma, which formatters
 *     add and remove, and</li>
 *     <li>the order of its modifiers, which google-java-format puts in the order the JLS recommends.</li>
 * </ul>
 * Imports are first put in the order the formatter left them, without those it removed. The result is only
 * returned when it prints exactly as the formatter's output, so anything else the formatter changed makes this
 * return {@code null}.
 */
final class WhitespaceReconciler {

    private static final Set<String> SYNTAX_MARKERS = new HashSet<>(Arrays.asList(
            "org.openrewrite.java.marker.Semicolon",
            "org.openrewrite.java.marker.TrailingComma",
            "org.openrewrite.kotlin.marker.Semicolon"));

    private WhitespaceReconciler() {
    }

    /**
     * Called by a visitor of the source file's language with each tree before it is visited, each {@link Space}
     * and each set of {@link Markers}, in the order it visits them, to return what to keep in their place.
     */
    interface Listener {
        Tree onTree(Tree tree);

        @Nullable Space onSpace(@Nullable Space space, Space.Location location);

        Markers onMarkers(Markers markers);
    }

    /**
     * @param visitorFactory makes a visitor of the source file's language that calls the listener
     * @return the original with the formatted copy's formatting, or {@code null} when the two trees differ by
     * more than that
     */
    static <S extends SourceFile> @Nullable S reconcile(S original, S formatted, String formattedSource,
                                                     Function<Listener, TreeVisitor<?, Integer>> visitorFactory) {
        if (!(original instanceof JavaSourceFile) || !(formatted instanceof JavaSourceFile)) {
            return null;
        }
        JavaSourceFile aligned = alignImports((JavaSourceFile) original, (JavaSourceFile) formatted);
        if (aligned == null) {
            return null;
        }

        List<Tree> trees = new ArrayList<>();
        List<@Nullable Space> spaces = new ArrayList<>();
        List<Space.Location> locations = new ArrayList<>();
        List<Markers> markers = new ArrayList<>();
        visitorFactory.apply(new Listener() {
            @Override
            public Tree onTree(Tree tree) {
                trees.add(tree);
                return tree;
            }

            @Override
            public @Nullable Space onSpace(@Nullable Space space, Space.Location location) {
                spaces.add(space);
                locations.add(location);
                return space;
            }

            @Override
            public Markers onMarkers(Markers m) {
                markers.add(m);
                return m;
            }
        }).visit(formatted, 0);

        Applier applier = new Applier(trees, spaces, locations, markers);
        Object reconciled = visitorFactory.apply(applier).visit(aligned, 0);
        if (applier.mismatched || applier.tree != trees.size() || applier.space != spaces.size() ||
            applier.marker != markers.size() || !(reconciled instanceof SourceFile)) {
            return null;
        }
        //noinspection unchecked
        S result = (S) reconciled;
        return formattedSource.equals(result.printAll(new PrintOutputCapture<>(0, PrintOutputCapture.MarkerPrinter.SANITIZED))) ?
                result : null;
    }

    private static final class Applier implements Listener {
        private final List<Tree> trees;
        private final List<@Nullable Space> spaces;
        private final List<Space.Location> locations;
        private final List<Markers> markers;
        int tree;
        int space;
        int marker;
        boolean mismatched;

        Applier(List<Tree> trees, List<@Nullable Space> spaces, List<Space.Location> locations, List<Markers> markers) {
            this.trees = trees;
            this.spaces = spaces;
            this.locations = locations;
            this.markers = markers;
        }

        @Override
        public Tree onTree(Tree t) {
            int i = tree++;
            if (mismatched || i >= trees.size() || trees.get(i).getClass() != t.getClass()) {
                mismatched = true;
                return t;
            }
            Tree formatted = trees.get(i);
            if (t instanceof J.ClassDeclaration) {
                J.ClassDeclaration c = (J.ClassDeclaration) t;
                List<J.Modifier> m = reorder(c.getModifiers(), ((J.ClassDeclaration) formatted).getModifiers());
                return m == c.getModifiers() ? t : c.withModifiers(m);
            } else if (t instanceof J.MethodDeclaration) {
                J.MethodDeclaration md = (J.MethodDeclaration) t;
                List<J.Modifier> m = reorder(md.getModifiers(), ((J.MethodDeclaration) formatted).getModifiers());
                return m == md.getModifiers() ? t : md.withModifiers(m);
            } else if (t instanceof J.VariableDeclarations) {
                J.VariableDeclarations v = (J.VariableDeclarations) t;
                List<J.Modifier> m = reorder(v.getModifiers(), ((J.VariableDeclarations) formatted).getModifiers());
                return m == v.getModifiers() ? t : v.withModifiers(m);
            }
            return t;
        }

        /**
         * The original's modifiers in the formatted copy's order, when they are the same modifiers.
         */
        private List<J.Modifier> reorder(List<J.Modifier> original, List<J.Modifier> formatted) {
            if (original.size() != formatted.size() || original.size() < 2) {
                return original;
            }
            List<J.Modifier> remaining = new ArrayList<>(original);
            List<J.Modifier> reordered = new ArrayList<>(original.size());
            boolean same = true;
            for (int i = 0; i < formatted.size(); i++) {
                J.Modifier f = formatted.get(i);
                J.Modifier match = null;
                for (J.Modifier o : remaining) {
                    if (o.getType() == f.getType() && Objects.equals(o.getKeyword(), f.getKeyword()) &&
                        o.getAnnotations().size() == f.getAnnotations().size()) {
                        match = o;
                        break;
                    }
                }
                if (match == null) {
                    return original;
                }
                remaining.remove(match);
                reordered.add(match);
                same &= match == original.get(i);
            }
            return same ? original : reordered;
        }

        @Override
        public @Nullable Space onSpace(@Nullable Space s, Space.Location location) {
            int i = space++;
            if (mismatched || i >= spaces.size() || locations.get(i) != location) {
                mismatched = true;
                return s;
            }
            Space replacement = spaces.get(i);
            return s == null || replacement == null || sameSpace(s, replacement) ? s : replacement;
        }

        @Override
        public Markers onMarkers(Markers m) {
            int i = marker++;
            if (mismatched || i >= markers.size()) {
                mismatched = true;
                return m;
            }
            List<Marker> formattedSyntax = syntaxMarkers(markers.get(i));
            List<Marker> originalSyntax = syntaxMarkers(m);
            if (formattedSyntax.isEmpty() && originalSyntax.isEmpty()) {
                return m;
            }
            List<Marker> kept = new ArrayList<>(m.getMarkers().size() + formattedSyntax.size());
            for (Marker existing : m.getMarkers()) {
                if (!SYNTAX_MARKERS.contains(existing.getClass().getName())) {
                    kept.add(existing);
                }
            }
            kept.addAll(formattedSyntax);
            return m.withMarkers(kept);
        }

        private static List<Marker> syntaxMarkers(Markers m) {
            List<Marker> syntax = new ArrayList<>(0);
            for (Marker existing : m.getMarkers()) {
                if (SYNTAX_MARKERS.contains(existing.getClass().getName())) {
                    syntax.add(existing);
                }
            }
            return syntax;
        }
    }

    private static boolean sameSpace(Space a, Space b) {
        return a == b || a.getWhitespace().equals(b.getWhitespace()) && a.getComments().isEmpty() && b.getComments().isEmpty();
    }

    /**
     * Gives the original the formatter's import order, keeping the original {@link J.Import} of each, with its
     * types. Returns {@code null} when the formatter wrote an import the original does not have.
     */
    private static @Nullable JavaSourceFile alignImports(JavaSourceFile original, JavaSourceFile formatted) {
        List<JRightPadded<J.Import>> originalImports = original.getPadding().getImports();
        List<JRightPadded<J.Import>> formattedImports = formatted.getPadding().getImports();
        Map<String, Deque<JRightPadded<J.Import>>> byKey = new HashMap<>();
        for (JRightPadded<J.Import> imp : originalImports) {
            byKey.computeIfAbsent(key(imp.getElement(), original), k -> new ArrayDeque<>()).add(imp);
        }
        List<JRightPadded<J.Import>> reordered = new ArrayList<>(formattedImports.size());
        boolean same = originalImports.size() == formattedImports.size();
        for (int i = 0; i < formattedImports.size(); i++) {
            Deque<JRightPadded<J.Import>> candidates = byKey.get(key(formattedImports.get(i).getElement(), formatted));
            if (candidates == null || candidates.isEmpty()) {
                return null;
            }
            JRightPadded<J.Import> imp = candidates.poll();
            same &= imp == originalImports.get(i);
            reordered.add(imp);
        }
        return same ? original : original.getPadding().withImports(reordered);
    }

    private static String key(J.Import imp, JavaSourceFile sourceFile) {
        String printed = imp.printTrimmed(new Cursor(new Cursor(null, Cursor.ROOT_VALUE), sourceFile));
        StringBuilder key = new StringBuilder(printed.length());
        for (int i = 0; i < printed.length(); i++) {
            char c = printed.charAt(i);
            if (!Character.isWhitespace(c) && c != ';') {
                key.append(c);
            }
        }
        return key.toString();
    }

    /**
     * Visits every tree, {@link Space} and set of {@link Markers} of a Java source file.
     */
    static final class JavaFormatting extends JavaIsoVisitor<Integer> {
        private final Listener listener;

        JavaFormatting(Listener listener) {
            this.listener = listener;
        }

        @Override
        public @Nullable J visit(@Nullable Tree tree, Integer p) {
            return super.visit(tree == null ? null : listener.onTree(tree), p);
        }

        @Override
        @SuppressWarnings("DataFlowIssue")
        public Space visitSpace(@Nullable Space space, Space.Location loc, Integer p) {
            return listener.onSpace(space, loc);
        }

        @Override
        public Markers visitMarkers(@Nullable Markers markers, Integer p) {
            return markers == null ? Markers.EMPTY : listener.onMarkers(markers);
        }
    }
}
