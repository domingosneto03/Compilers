package pt.up.fe.comp2025.analysis;

import pt.up.fe.comp.jmm.analysis.JmmAnalysis;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.passes.*;
import pt.up.fe.comp2025.symboltable.JmmSymbolTableBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of the semantic analysis stage.
 */
public class JmmAnalysisImpl implements JmmAnalysis {

    /**
     * Analysis passes that will be applied to the AST.
     *
     * @param table The symbol table for the program.
     * @return A list of AnalysisVisitor instances.
     */
    private List<AnalysisVisitor> buildPasses(SymbolTable table) {
        return List.of(
                new UndeclaredVariable(),
                new BinaryOperationCheck(),
                new ArrayArithmeticCheck(),
                new ArrayAccessCombinedCheck(),
                new ReturnCheckVisitor(),
                new MethodVerificationVisitor(),
                new AssignmentTypeCheck(),
                new ConditionCheck(),
                new ArrayInitializerUsageCheck(),
                new DuplicateDeclarationCheck(),
                new VarargsDeclarationCheck()
        );
    }

    @Override
    public JmmSemanticsResult buildSymbolTable(JmmParserResult parserResult) {
        JmmNode rootNode = parserResult.getRootNode();

        // Constrói a symbol table e recolhe erros de duplicados
        var symbolTableBuilder = new JmmSymbolTableBuilder();
        SymbolTable table = symbolTableBuilder.build(rootNode);
        List<Report> initialReports = symbolTableBuilder.getReports();

        return new JmmSemanticsResult(parserResult, table, initialReports);
    }

    @Override
    public JmmSemanticsResult semanticAnalysis(JmmSemanticsResult semanticsResult) {
        var table = semanticsResult.getSymbolTable();
        var rootNode = semanticsResult.getRootNode();

        // Começa com quaisquer reports da construção da symbol table
        var reports = new ArrayList<>(semanticsResult.getReports());

        // Se já houver erros, não prossegue com os passes semânticos
        boolean hasInitialErrors = reports.stream()
                .anyMatch(r -> r.getType() == ReportType.ERROR);
        if (hasInitialErrors) {
            return new JmmSemanticsResult(semanticsResult, reports);
        }

        // Executa cada pass em sequência
        var analysisVisitors = buildPasses(table);
        for (var analysisVisitor : analysisVisitors) {
            try {
                var passReports = analysisVisitor.analyze(rootNode, table);
                reports.addAll(passReports);

                // Interrompe se algum erro for reportado
                boolean hasError = passReports.stream()
                        .anyMatch(r -> r.getType() == ReportType.ERROR);
                if (hasError) {
                    return new JmmSemanticsResult(semanticsResult, reports);
                }
            } catch (Exception e) {
                // Captura exceções inesperadas durante o pass
                reports.add(Report.newError(
                        Stage.SEMANTIC,
                        -1,
                        -1,
                        "Problem while executing analysis pass '" +
                                analysisVisitor.getClass().getSimpleName() + "'",
                        e
                ));
                return new JmmSemanticsResult(semanticsResult, reports);
            }
        }

        return new JmmSemanticsResult(semanticsResult, reports);
    }
}
