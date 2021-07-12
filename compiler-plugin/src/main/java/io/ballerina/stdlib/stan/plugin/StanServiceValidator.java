/*
 * Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.stdlib.stan.plugin;

import io.ballerina.compiler.api.SemanticModel;
import io.ballerina.compiler.api.symbols.AnnotationSymbol;
import io.ballerina.compiler.api.symbols.MethodSymbol;
import io.ballerina.compiler.api.symbols.ModuleSymbol;
import io.ballerina.compiler.api.symbols.ServiceAttachPoint;
import io.ballerina.compiler.api.symbols.ServiceAttachPointKind;
import io.ballerina.compiler.api.symbols.ServiceDeclarationSymbol;
import io.ballerina.compiler.api.symbols.Symbol;
import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NodeList;
import io.ballerina.compiler.syntax.tree.ServiceDeclarationNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.projects.plugins.SyntaxNodeAnalysisContext;
import io.ballerina.stdlib.stan.plugin.PluginConstants.CompilationErrors;
import io.ballerina.tools.diagnostics.DiagnosticSeverity;
import io.ballerina.tools.diagnostics.Location;

import java.util.List;
import java.util.Optional;

/**
 * STAN service compilation validator.
 */
public class StanServiceValidator {

    public void validate(SyntaxNodeAnalysisContext context) {
        ServiceDeclarationNode serviceDeclarationNode = (ServiceDeclarationNode) context.node();
        NodeList<Node> memberNodes = serviceDeclarationNode.members();
        validateAttachPoint(context);
        FunctionDefinitionNode onMessage = null;
        FunctionDefinitionNode onError = null;

        for (Node node : memberNodes) {
            FunctionDefinitionNode functionDefinitionNode = (FunctionDefinitionNode) node;
            if (node.kind() == SyntaxKind.OBJECT_METHOD_DEFINITION) {
                MethodSymbol methodSymbol = PluginUtils.getMethodSymbol(context, functionDefinitionNode);
                Optional<String> functionName = methodSymbol.getName();
                if (functionName.isPresent()) {
                    if (functionName.get().equals(PluginConstants.ON_MESSAGE_FUNC)) {
                        onMessage = functionDefinitionNode;
                    } else if (functionName.get().equals(PluginConstants.ON_ERROR_FUNC)) {
                        onError = functionDefinitionNode;
                    } else {
                        validateNonStanFunction(functionDefinitionNode, context);
                    }
                }
            } else {
                validateNonStanFunction(functionDefinitionNode, context);
            }
        }
        new StanFunctionValidator(context, onMessage, onError).validate();
    }

    public void validateNonStanFunction(FunctionDefinitionNode functionDefinitionNode,
                                        SyntaxNodeAnalysisContext context) {
        if (functionDefinitionNode.kind() == SyntaxKind.RESOURCE_ACCESSOR_DEFINITION) {
            context.reportDiagnostic(PluginUtils.getDiagnostic(CompilationErrors.INVALID_FUNCTION,
                    DiagnosticSeverity.ERROR, functionDefinitionNode.location()));
        } else {
            if (PluginUtils.isRemoteFunction(context, functionDefinitionNode)) {
                context.reportDiagnostic(PluginUtils.getDiagnostic(CompilationErrors.INVALID_REMOTE_FUNCTION,
                        DiagnosticSeverity.ERROR, functionDefinitionNode.location()));
            }
        }
    }


    private void validateAttachPoint(SyntaxNodeAnalysisContext context) {
        SemanticModel semanticModel = context.semanticModel();
        ServiceDeclarationNode serviceDeclarationNode = (ServiceDeclarationNode) context.node();
        Optional<Symbol> symbol = semanticModel.symbol(serviceDeclarationNode);
        if (symbol.isPresent()) {
            ServiceDeclarationSymbol serviceDeclarationSymbol = (ServiceDeclarationSymbol) symbol.get();
            Optional<ServiceAttachPoint> attachPoint = serviceDeclarationSymbol.attachPoint();
            List<AnnotationSymbol> symbolList = serviceDeclarationSymbol.annotations();
            if (attachPoint.isEmpty()) {
                if (symbolList.isEmpty()) {
                    context.reportDiagnostic(PluginUtils.getDiagnostic(CompilationErrors.NO_ANNOTATION,
                            DiagnosticSeverity.ERROR, serviceDeclarationNode.location()));
                } else if (symbolList.size() > 1) {
                    context.reportDiagnostic(PluginUtils.getDiagnostic(CompilationErrors.INVALID_ANNOTATION_NUMBER,
                            DiagnosticSeverity.ERROR, serviceDeclarationNode.location()));
                } else {
                    validateAnnotation(symbolList.get(0), serviceDeclarationNode.location(), context);
                }
            } else {
                if (attachPoint.get().kind() != ServiceAttachPointKind.STRING_LITERAL) {
                    if (serviceDeclarationSymbol.annotations().isEmpty()) {
                        context.reportDiagnostic(PluginUtils.getDiagnostic(
                                CompilationErrors.INVALID_SERVICE_ATTACH_POINT,
                                DiagnosticSeverity.ERROR, serviceDeclarationNode.location()));
                    } else {
                        validateAnnotation(symbolList.get(0), serviceDeclarationNode.location(), context);
                    }
                }
            }
        }
    }

    private void validateAnnotation(AnnotationSymbol annotationSymbol, Location location,
                                    SyntaxNodeAnalysisContext context) {
        Optional<ModuleSymbol> moduleSymbolOptional = annotationSymbol.getModule();
        if (moduleSymbolOptional.isEmpty()) {
            context.reportDiagnostic(PluginUtils.getDiagnostic(CompilationErrors.INVALID_ANNOTATION,
                    DiagnosticSeverity.ERROR, location));
        } else {
            ModuleSymbol moduleSymbol = moduleSymbolOptional.get();
            if (!moduleSymbol.id().orgName().equals(PluginConstants.PACKAGE_ORG) ||
                    !moduleSymbol.id().moduleName().equals(PluginConstants.PACKAGE_PREFIX)) {
                context.reportDiagnostic(PluginUtils.getDiagnostic(CompilationErrors.INVALID_ANNOTATION,
                        DiagnosticSeverity.ERROR, location));
            }
        }
    }
}
