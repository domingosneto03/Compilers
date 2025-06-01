package pt.up.fe.comp2025.analysis;

import pt.up.fe.comp.jmm.analysis.JmmAnalysis;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.passes.*;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.symboltable.JmmSymbolTableBuilder;
import pt.up.fe.comp2025.analysis.passes.MethodVerificationVisitor;


import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of the semantic analysis stage.
 */
public class JmmAnalysisImpl implements JmmAnalysis {


    /**
     * Analysis passes that will be applied to the AST.
     *
     * @param table
     * @return
     */
    private List<AnalysisVisitor> buildPasses(SymbolTable table) {
        return List.of(
                new DuplicateCheck(),
                new UndeclaredVariable(),
                new VariableDeclarationOrderCheck(),
                new VoidTypeVariableCheck(),
                new BinaryOperationCheck(),
                new ArrayArithmeticCheck(),
                new ArrayAccessCombinedCheck(),
                new MethodVerificationVisitor(),
                new AssignmentTypeCheck(),
                new ConditionCheck(),
                new ArrayInitializerUsageCheck(),
                new VarargsCheck(),
                new StaticMethodFieldAccessCheck(),
                new ReturnTypeCheck()
        );


    }

    @Override
    public JmmSemanticsResult buildSymbolTable(JmmParserResult parserResult) {
        JmmNode rootNode = parserResult.getRootNode();

        JmmSymbolTableBuilder builder = new JmmSymbolTableBuilder();
        SymbolTable table = builder.build(rootNode);

        List<Report> reports = builder.getReports();

        return new JmmSemanticsResult(parserResult, table, reports);
    }

    @Override
    public JmmSemanticsResult semanticAnalysis(JmmSemanticsResult semanticsResult) {

        var table = semanticsResult.getSymbolTable();

        var analysisVisitors = buildPasses(table);

        var rootNode = semanticsResult.getRootNode();

        var reports = new ArrayList<Report>();

        // This is a simple implementation that assumes all passes are implemented as visitors, each one making a full visit of the AST.
        // There are other implementations that reduce the number of full AST visits, this is not required for the work, but a nice challenge if you want to try.
        for (var analysisVisitor : analysisVisitors) {
            try {
                var passReports = analysisVisitor.analyze(rootNode, table);

                var hasSymbolTableErrors = passReports.stream()
                        .anyMatch(report -> report.getType() == ReportType.ERROR);


                reports.addAll(passReports);

                // Return early in case of error report
                if (hasSymbolTableErrors) {
                    System.out.println("Found errors: " + reports);
                    return new JmmSemanticsResult(semanticsResult, reports);
                }

            } catch (Exception e) {
                reports.add(Report.newError(Stage.SEMANTIC,
                        -1,
                        -1,
                        "Problem while executing analysis pass '" + analysisVisitor.getClass() + "'",
                        e)
                );
                System.out.println("Exception: " + reports);
            }

        }


        return new JmmSemanticsResult(semanticsResult, reports);
    }


}
