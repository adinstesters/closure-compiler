/*
 * Copyright 2016 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.javascript.jscomp;

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.modules.ModuleMap;
import com.google.javascript.jscomp.modules.ModuleMetadataMap;
import com.google.javascript.rhino.Node;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class PolymerClassRewriterTest extends CompilerTypeTestCase {

  private static final String EXTERNS =
      lines(
          "/** @constructor */",
          "var HTMLElement = function() {};",
          "/** @constructor @extends {HTMLElement} */",
          "var HTMLInputElement = function() {};",
          "/** @constructor @extends {HTMLElement} */",
          "var PolymerElement = function() {};",
          "/** @type {!Object} */",
          "PolymerElement.prototype.$;",
          "PolymerElement.prototype.created = function() {};",
          "PolymerElement.prototype.ready = function() {};",
          "PolymerElement.prototype.attached = function() {};",
          "PolymerElement.prototype.domReady = function() {};",
          "PolymerElement.prototype.detached = function() {};",
          "/**",
          " * Call the callback after a timeout. Calling job again with the same name",
          " * resets the timer but will not result in additional calls to callback.",
          " *",
          " * @param {string} name",
          " * @param {Function} callback",
          " * @param {number} timeoutMillis The minimum delay in milliseconds before",
          " *     calling the callback.",
          " */",
          "PolymerElement.prototype.job = function(name, callback, timeoutMillis) {};",
          "/**",
          " * @param a {!Object}",
          " * @return {!function()}",
          " */",
          "var Polymer = function(a) {};",
          "var alert = function(msg) {};");

  private PolymerClassRewriter rewriter;
  private Node rootNode;
  private GlobalNamespace globalNamespace;
  private Node polymerCall;
  private boolean inGlobalScope;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    polymerCall = null;
    rootNode = null;
    inGlobalScope = true;
  }

  // TODO(jlklein): Add tests for non-global definitions, interface externs, read-only setters, etc.

  @Test
  public void testPolymerWriterGeneratesCodeInsideLoadModule() {
    inGlobalScope = false;
    test(
        lines(
            "goog.loadModule(function(exports) {",
            "  goog.module('ytu.app.ui.shared.YtuIcon');",
            "  YtuIcon = Polymer({is: 'ytu-icon' });",
            "  exports = YtuIcon;",
            "  return exports;",
            "})"),
        lines(
            "goog.loadModule(function(exports) {",
            "  goog.module('ytu.app.ui.shared.YtuIcon');",
            "  var YtuIcon = function(){}",
            "  YtuIcon = Polymer(/** @lends {YtuIcon.prototype} */ {is:\"ytu-icon\"});",
            "  exports = YtuIcon;",
            "  return exports;",
            "})"));
  }

  @Test
  public void testVarTarget() {
    test(
        lines(
            "var X = Polymer({",
            "  is: 'x-element',",
            "});"),
        lines(
            "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface} */",
            "var X = function() {};",
            "X = Polymer(/** @lends {X.prototype} */ {",
            "  is: 'x-element',",
            "});"));

    setLanguageLevel(LanguageMode.ECMASCRIPT_2015);
    testSame(
        lines(
            "var X = class extends Polymer.Element {",
            "  static get is() { return 'x-element'; }",
            "  static get properties { return { }; }",
            "};"));
  }

  @Test
  public void testVarTarget_inGoogModule() {
    inGlobalScope = false;
    test(
        lines(
            "goog.module('mod');", //
            "var X = Polymer({",
            "  is: 'x-element',",
            "});"),
        lines(
            "goog.module('mod');",
            "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface} */",
            "var X = function() {};",
            "X = Polymer(/** @lends {X.prototype} */ {",
            "  is: 'x-element',",
            "});"));

    testSame(
        lines(
            "goog.module('mod');",
            "var X = class extends Polymer.Element {",
            "  static get is() { return 'x-element'; }",
            "  static get properties { return { }; }",
            "};"));
  }

  @Test
  public void testVarTarget_inIifeInGoogModule() {
    inGlobalScope = false;
    test(
        lines(
            "goog.module('mod');", //
            "(function() {",
            "  var X = Polymer({",
            "    is: 'x-element',",
            "  });",
            "})();"),
        lines(
            "goog.module('mod');",
            "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface} */",
            "var X = function() {};",
            "(function() {",
            "    X = Polymer(/** @lends {X.prototype} */ {",
            "    is: 'x-element',",
            "  });",
            "})();"));
  }

  @Test
  public void testVarTarget_inGoogModuleWithRequires() {
    inGlobalScope = false;
    test(
        lines(
            "goog.module('mod');", //
            "const Component = goog.require('goog.Component');",
            "goog.forwardDeclare('something.else');",
            "const someLocal = (function() { return 0; })();",
            "var X = Polymer({",
            "  is: 'x-element',",
            "});"),
        lines(
            "goog.module('mod');",
            "const Component = goog.require('goog.Component');",
            "goog.forwardDeclare('something.else');",
            "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface} */",
            "var X = function() {};",
            "const someLocal = (function() { return 0; })();",
            "X = Polymer(/** @lends {X.prototype} */ {",
            "  is: 'x-element',",
            "});"));
  }

  @Test
  public void testVarTarget_inEsModule() {
    inGlobalScope = false;
    test(
        lines(
            "var X = Polymer({", //
            "  is: 'x-element',",
            "});",
            "export {X};"),
        lines(
            "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface} */",
            "var X = function() {};",
            "X = Polymer(/** @lends {X.prototype} */ {",
            "  is: 'x-element',",
            "});",
            "export {X};"));

    testSame(
        lines(
            "var X = class extends Polymer.Element {",
            "  static get is() { return 'x-element'; }",
            "  static get properties { return { }; }",
            "};",
            "export {X};"));
  }

  @Test
  public void testDefaultTypeNameTarget() {
    test(
        lines(
            "Polymer({",
            "  is: 'x',",
            "});"),
        lines(
            "/**",
            " * @implements {PolymerXElementInterface}",
            " * @constructor @extends {PolymerElement}",
            " */",
            "var XElement = function() {};",
            "Polymer(/** @lends {XElement.prototype} */ {",
            "  is: 'x',",
            "});"));
  }

  @Test
  public void testPathAssignmentTarget() {
    test(
        lines(
            "var x = {};",
            "x.Z = Polymer({",
            "  is: 'x-element',",
            "});"),
        lines(
            "var x = {};",
            "/** @constructor @extends {PolymerElement} @implements {Polymerx_ZInterface} */",
            "x.Z = function() {};",
            "x.Z = Polymer(/** @lends {x.Z.prototype} */ {",
            "  is: 'x-element',",
            "});"));
  }

  @Override
  protected CompilerOptions getDefaultOptions() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT5);
    options.setWarningLevel(
        DiagnosticGroups.MISSING_PROPERTIES, CheckLevel.WARNING);
    options.setWarningLevel(
        DiagnosticGroups.MISPLACED_TYPE_ANNOTATION, CheckLevel.WARNING);
    options.setWarningLevel(
        DiagnosticGroups.INVALID_CASTS, CheckLevel.WARNING);
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);
    options.setWarningLevel(DiagnosticGroups.MODULE_LOAD, CheckLevel.OFF);
    options.setCodingConvention(getCodingConvention());
    options.setPreserveTypeAnnotations(true);
    options.setPrettyPrint(true);
    return options;
  }

  private void test(String originalCode, String expectedResult) {
    parseAndRewrite(originalCode, 1);
    Node expectedNode = compiler.parseSyntheticCode(expectedResult);
    assertNode(rootNode).isEqualTo(expectedNode);

    parseAndRewrite(originalCode, 2);
    expectedNode = compiler.parseSyntheticCode(expectedResult);
    assertNode(rootNode).isEqualTo(expectedNode);
  }

  private void testSame(String originalCode) {
    parseAndRewrite(originalCode, 1);
    Node expectedNode = compiler.parseSyntheticCode(originalCode);
    assertNode(rootNode).isEqualTo(expectedNode);

    parseAndRewrite(originalCode, 2);
    expectedNode = compiler.parseSyntheticCode(originalCode);
    assertNode(rootNode).isEqualTo(expectedNode);
  }

  private void parseAndRewrite(String code, int version) {
    rootNode = compiler.parseTestCode(code);
    globalNamespace =  new GlobalNamespace(compiler, rootNode);
    PolymerPassFindExterns findExternsCallback = new PolymerPassFindExterns();
    Node externs = compiler.parseTestCode(EXTERNS);
    NodeTraversal.traverse(compiler, externs, findExternsCallback);

    rewriter =
        new PolymerClassRewriter(
            compiler,
            findExternsCallback.getPolymerElementExterns(),
            version,
            PolymerExportPolicy.LEGACY,
            true);

    NodeUtil.visitPostOrder(
        rootNode,
        new NodeUtil.Visitor() {
          @Override
          public void visit(Node node) {
            if (PolymerPassStaticUtils.isPolymerCall(node)) {
              polymerCall = node;
            }
          }
        });

    assertThat(polymerCall).isNotNull();
    PolymerClassDefinition classDef =
        PolymerClassDefinition.extractFromCallNode(
            polymerCall,
            compiler,
            /* moduleMetadata= */ null,
            new PolymerBehaviorExtractor(
                compiler,
                globalNamespace,
                ModuleMetadataMap.emptyForTesting(),
                ModuleMap.emptyForTesting()));

    Node parent = polymerCall.getParent();
    Node grandparent = parent.getParent();
    if (NodeUtil.isNameDeclaration(grandparent) || parent.isAssign()) {
      rewriter.rewritePolymerCall(grandparent, classDef, inGlobalScope);
    } else {
      rewriter.rewritePolymerCall(parent, classDef, inGlobalScope);
    }
  }

  private void setLanguageLevel(LanguageMode mode) {
    compiler.getOptions().setLanguageIn(mode);
  }
}
