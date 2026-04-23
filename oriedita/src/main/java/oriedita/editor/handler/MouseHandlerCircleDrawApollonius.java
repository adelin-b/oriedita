package oriedita.editor.handler;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import oriedita.editor.canvas.MouseMode;
import oriedita.editor.drawing.tools.Camera;
import oriedita.editor.drawing.tools.DrawingUtil;
import oriedita.editor.handler.step.StepMouseHandler;
import oriedita.editor.handler.step.ObjCoordStepNode;
import origami.crease_pattern.OritaCalc;
import origami.crease_pattern.element.Circle;
import origami.crease_pattern.element.LineColor;
import origami.crease_pattern.element.LineSegment;
import origami.crease_pattern.element.Point;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

enum CircleDrawApolloniusStep {
    SELECT_1,
    SELECT_2,
    SELECT_3,
    SELECT_RESULT,
}

@ApplicationScoped
@Handles(MouseMode.CIRCLE_DRAW_APOLLONIUS_108)
public class MouseHandlerCircleDrawApollonius extends StepMouseHandler<CircleDrawApolloniusStep> {
    private Point p1,p2,p3;
    private LineSegment l1, l2, l3;
    private Circle k1, k2, k3;
    private List<Circle> indicators;
    private Circle result;



    @Inject
    public MouseHandlerCircleDrawApollonius() {
        super(CircleDrawApolloniusStep.SELECT_1);
        steps.addNode(ObjCoordStepNode.createNode_MD_R(CircleDrawApolloniusStep.SELECT_1,
                this::move_drag_select_1,
                this::release_select_1));
        steps.addNode(ObjCoordStepNode.createNode_MD_R(CircleDrawApolloniusStep.SELECT_2,
                this::move_drag_select_2,
                this::release_select_2));
        steps.addNode(ObjCoordStepNode.createNode_MD_R(CircleDrawApolloniusStep.SELECT_3,
                this::move_drag_select_3,
                this::release_select_3));
        steps.addNode(ObjCoordStepNode.createNode_MD_R(CircleDrawApolloniusStep.SELECT_RESULT,
                this::move_drag_select_result,
                this::release_select_result));
    }

    @Override
    public void drawPreview(Graphics2D g2, Camera camera, DrawingSettings settings) {
        super.drawPreview(g2, camera, settings);

        DrawingUtil.drawCircleStep(g2, k1, camera);
        DrawingUtil.drawCircleStep(g2, k2, camera);
        DrawingUtil.drawCircleStep(g2, k3, camera);

        for (Circle indicator : indicators)
            DrawingUtil.drawCircleStep(g2, indicator, camera);

        DrawingUtil.drawCircleStep(g2, result, camera);

        DrawingUtil.drawLineStep(g2, l1, camera, settings.getLineWidth());
        DrawingUtil.drawLineStep(g2, l2, camera, settings.getLineWidth());
        DrawingUtil.drawLineStep(g2, l3, camera, settings.getLineWidth());

        DrawingUtil.drawStepVertex(g2, p1, LineColor.GREEN_6, camera);
        DrawingUtil.drawStepVertex(g2, p2, LineColor.GREEN_6, camera);
        DrawingUtil.drawStepVertex(g2, p3, LineColor.GREEN_6, camera);
    }

    @Override
    public void reset() {
        resetStep();
        indicators = new ArrayList<>();
        p1 = null;
        p2 = null;
        p3 = null;

        l1 = null;
        l2 = null;
        l3 = null;

        k1 = null;
        k2 = null;
        k3 = null;
        result = null;
    }

    // Select first element
    private void move_drag_select_1(Point p) {
        Circle tmpCircle = d.getClosestCircleMidpoint(p);
        if (OritaCalc.distance_circumference(p, tmpCircle) < d.getSelectionDistance()) {
            k1 = new Circle(tmpCircle);
            k1.setColor(LineColor.GREEN_6);
        } else
            k1 = null;

        LineSegment tmpSegment = d.getClosestLineSegment(p);
        if (OritaCalc.determineLineSegmentDistance(p, tmpSegment) < d.getSelectionDistance()) {
            l1 = new LineSegment(tmpSegment, LineColor.GREEN_6);
            k1 = null;
        } else
            l1 = null;

        // The solver uses 0 radius circles as points, p1-3 are only used for display
        if (p.distance(d.getClosestPoint(p)) < d.getSelectionDistance()) {
            p1 = d.getClosestPoint(p);
            k1 = new Circle(p1.getX(), p1.getY(), 0, LineColor.CYAN_3);
            l1 = null;
        } else
            p1 = null;
    }

    private CircleDrawApolloniusStep release_select_1(Point p) {
        if (p1 == null && k1 == null && l1 == null)
            return CircleDrawApolloniusStep.SELECT_1;
        return CircleDrawApolloniusStep.SELECT_2;
    }

    // Select second element
    private void move_drag_select_2(Point p) {
        Circle tmpCircle = d.getClosestCircleMidpoint(p);
        if (OritaCalc.distance_circumference(p, tmpCircle) < d.getSelectionDistance()) {
            k2 = new Circle(tmpCircle);
            k2.setColor(LineColor.GREEN_6);
        } else
            k2 = null;

        LineSegment tmpSegment = d.getClosestLineSegment(p);
        if (OritaCalc.determineLineSegmentDistance(p, tmpSegment) < d.getSelectionDistance()) {
            l2 = new LineSegment(tmpSegment, LineColor.GREEN_6);
            k2 = null;
        } else
            l2 = null;

        // The solver uses 0 radius circles as points, p1-3 are only used for display
        if (p.distance(d.getClosestPoint(p)) < d.getSelectionDistance()) {
            p2 = d.getClosestPoint(p);
            k2 = new Circle(p2.getX(), p2.getY(), 0, LineColor.CYAN_3);
            l2 = null;
        } else
            p2 = null;
    }

    private CircleDrawApolloniusStep release_select_2(Point p) {
        if (p2 == null && k2 == null && l2 == null)
            return CircleDrawApolloniusStep.SELECT_2;
        return CircleDrawApolloniusStep.SELECT_3;
    }

    // Select second element
    private void move_drag_select_3(Point p) {
        Circle tmpCircle = d.getClosestCircleMidpoint(p);
        if (OritaCalc.distance_circumference(p, tmpCircle) < d.getSelectionDistance()) {
            k3 = new Circle(tmpCircle);
            k3.setColor(LineColor.GREEN_6);
        } else
            k3 = null;

        LineSegment tmpSegment = d.getClosestLineSegment(p);
        if (OritaCalc.determineLineSegmentDistance(p, tmpSegment) < d.getSelectionDistance()) {
            l3 = new LineSegment(tmpSegment, LineColor.GREEN_6);
            k3 = null;
        } else
            l3 = null;

        // The solver uses 0 radius circles as points, p1-3 are only used for display
        if (p.distance(d.getClosestPoint(p)) < d.getSelectionDistance()) {
            p3 = d.getClosestPoint(p);
            k3 = new Circle(p3.getX(), p3.getY(), 0, LineColor.CYAN_3);
            l3 = null;
        } else
            p3 = null;
    }

    private CircleDrawApolloniusStep release_select_3(Point p) {
        if (p3 == null && k3 == null && l3 == null)
            return CircleDrawApolloniusStep.SELECT_3;

        processApollonius();

        if(indicators.size() > 1)
            return CircleDrawApolloniusStep.SELECT_RESULT;
        else if(indicators.size() == 1) {
            indicators.get(0).setColor(LineColor.CYAN_3);
            d.addCircle(indicators.get(0));
            d.record();
            reset();
            return CircleDrawApolloniusStep.SELECT_1;
        }
        else {
            reset();
            return CircleDrawApolloniusStep.SELECT_1;
        }
    }

    // Select indicator
    private void move_drag_select_result(Point p) {
        double minDist = 100000.0;
        for (Circle indicator : indicators) {
            double dist = OritaCalc.distance_circumference(p, indicator);
            if (dist < minDist) {
                minDist = dist;
                if (dist < d.getSelectionDistance()) {
                    result = new Circle(indicator);
                    result.setColor(LineColor.ORANGE_4);
                } else
                    result = null;
            }
        }
    }

    private CircleDrawApolloniusStep release_select_result(Point p) {
        if (result == null)
            return CircleDrawApolloniusStep.SELECT_RESULT;

        result.setColor(LineColor.CYAN_3);
        d.addCircle(result);
        d.record();
        reset();
        return CircleDrawApolloniusStep.SELECT_1;
    }

    private void processApollonius() {
        int numC = ((k1 !=null) ? 1:0) + ((k2 !=null) ? 1:0) + ((k3 !=null) ? 1:0);

        // Reshuffle to make sure they are populated from 1 to 3. The selection process can e.g. leave l1 empty but have l2 populated
        if(l1 ==null && l3 !=null)    l1 = l3;
        if(l1 ==null && l2 !=null)    l1 = l2;
        if(l2 ==null && l3 !=null)    l2 = l3;

        if(k1==null && k3!=null)    k1 = k3;
        if(k1==null && k2!=null)    k1 = k2;
        if(k2==null && k3!=null)    k2 = k3;

        // Points are represented by circles with radius 0, so 6/10 variants are redundant
        if(numC==0)  processApollonius_LLL();
        if(numC==1)  processApollonius_CLL();
        if(numC==2)  processApollonius_CCL();
        if(numC==3)  processApollonius_CCC();
    }

    private void processApollonius_LLL() {
        final double EPS = 1e-6;
        final double MIN_R = 1e-3;
        final double MAX_R = 1e6;

        int[] signs = {-1, 1};

        for (int s1 : signs) {
            for (int s2 : signs) {
                for (int s3 : signs) {

                    Circle c = solveLLL(s1, s2, s3);
                    if (c == null) continue;

                    if (c.getR() < MIN_R || c.getR() > MAX_R) continue;

                    if (!isDuplicate(c, indicators, EPS)) {
                        indicators.add(c);
                    }
                }
            }
        }
    }
    private Circle solveLLL(int s1, int s2, int s3) {

        double[][] lin = new double[3][4];

        lin[0] = coeffLinEquPar(l1, s1);
        lin[1] = coeffLinEquPar(l2, s2);
        lin[2] = coeffLinEquPar(l3, s3);

        double det0 =
                lin[0][1]*lin[1][2]*lin[2][3] +
                        lin[0][2]*lin[1][3]*lin[2][1] +
                        lin[0][3]*lin[1][1]*lin[2][2]
                        - lin[2][1]*lin[1][2]*lin[0][3]
                        - lin[2][2]*lin[1][3]*lin[0][1]
                        - lin[2][3]*lin[1][1]*lin[0][2];

        if (Math.abs(det0) < 1e-9) return null;

        double detX =
                lin[0][0]*lin[1][2]*lin[2][3] +
                        lin[0][2]*lin[1][3]*lin[2][0] +
                        lin[0][3]*lin[1][0]*lin[2][2]
                        - lin[2][0]*lin[1][2]*lin[0][3]
                        - lin[2][2]*lin[1][3]*lin[0][0]
                        - lin[2][3]*lin[1][0]*lin[0][2];

        double detY =
                lin[0][1]*lin[1][0]*lin[2][3] +
                        lin[0][0]*lin[1][3]*lin[2][1] +
                        lin[0][3]*lin[1][1]*lin[2][0]
                        - lin[2][1]*lin[1][0]*lin[0][3]
                        - lin[2][0]*lin[1][3]*lin[0][1]
                        - lin[2][3]*lin[1][1]*lin[0][0];

        double detR =
                lin[0][1]*lin[1][2]*lin[2][0] +
                        lin[0][2]*lin[1][0]*lin[2][1] +
                        lin[0][0]*lin[1][1]*lin[2][2]
                        - lin[2][1]*lin[1][2]*lin[0][0]
                        - lin[2][2]*lin[1][0]*lin[0][1]
                        - lin[2][0]*lin[1][1]*lin[0][2];

        double x = detX / det0;
        double y = detY / det0;
        double r = detR / det0;

        if (r <= 0) return null;

        return new Circle(x, y, r, LineColor.CYAN_3);
    }

    private void processApollonius_CLL() {
        final double EPS = 1e-6;
        final double MIN_R = 1e-3;
        final double MAX_R = 1e6;

        int[] signs = {-1, 1}; // s1 (circle), s2, s3 (lines)

        for (int s1 : signs) {
            for (int s2 : signs) {
                for (int s3 : signs) {

                    Circle[] sols = solveCLL(s1, s2, s3);

                    for (Circle c : sols) {
                        if (c == null) continue;

                        if (c.getR() < MIN_R || c.getR() > MAX_R) continue;

                        if (!isDuplicate(c, indicators, EPS)) {
                            indicators.add(c);
                        }
                    }
                }
            }
        }
    }
    private Circle[] solveCLL(int s1, int s2, int s3) {

        double[][] lin = new double[2][4];

        lin[0] = coeffLinEquPar(l1, s2);
        lin[1] = coeffLinEquPar(l2, s3);

        double[][] cd = solution23(lin);

        double c1 = cd[0][0], d1 = cd[0][1];
        double c2 = cd[1][0], d2 = cd[1][1];

        double e1 = d1 - k1.getX();
        double e2 = d2 - k1.getY();

        double kr = k1.getR();

        double denom = 1 - c1*c1 - c2*c2;
        if (Math.abs(denom) < 1e-9) return new Circle[]{null, null};

        double discr =
                (c1*c1 + c2*c2) * kr * kr +
                        2 * (c1*e1 + c2*e2) * kr * s1 +
                        (1 - c1*c1) * e2 * e2 +
                        (1 - c2*c2) * e1 * e1 +
                        2 * c1 * c2 * e1 * e2;

        if (discr < 0) return new Circle[]{null, null};

        double root = Math.sqrt(discr);
        double h = kr * s1 + c1*e1 + c2*e2;

        double r1 = (h + root) / denom;
        double r2 = (h - root) / denom;

        Circle[] result = new Circle[2];

        if (r1 > 0) {
            double x = c1*r1 + d1;
            double y = c2*r1 + d2;
            result[0] = new Circle(x, y, r1, LineColor.CYAN_3);
        }

        if (r2 > 0 && discr > 1e-12) {
            double x = c1*r2 + d1;
            double y = c2*r2 + d2;
            result[1] = new Circle(x, y, r2, LineColor.CYAN_3);
        }

        return result;
    }
    private void processApollonius_CCL() {
        final double EPS = 1e-6;
        final double MIN_R = 1e-3;
        final double MAX_R = 1e6;

        int[] signs = {-1, 1};

        for (int s1 : signs) {
            for (int s2 : signs) {
                for (int s3 : signs) {

                    Circle[] sols = solveCCL(s1, s2, s3);

                    for (Circle c : sols) {
                        if (c == null) continue;

                        if (c.getR() < MIN_R || c.getR() > MAX_R) continue;

                        if (!isDuplicate(c, indicators, EPS)) {
                            indicators.add(c);
                        }
                    }
                }
            }
        }
    }

    private Circle[] solveCCL(int s1, int s2, int s3) {

        double[][] lin = new double[2][4];
        Circle k1 = this.k1;
        Circle k2 = this.k2;
        LineSegment g = l1;

        lin[0] = coeffLinEqu(k1, s1, k2, s2);
        lin[1] = coeffLinEquPar(g, s3);

        double[][] cd = solution23(lin);

        double c1 = cd[0][0], d1 = cd[0][1];
        double c2 = cd[1][0], d2 = cd[1][1];

        double e1 = d1 - k1.getX();
        double e2 = d2 - k1.getY();

        double rK = k1.getR();

        double denom = 1 - c1*c1 - c2*c2;
        if (Math.abs(denom) < 1e-9) return new Circle[]{null, null};

        double discr =
                (c1*c1 + c2*c2) * rK * rK +
                        2 * (c1*e1 + c2*e2) * rK * s1 +
                        (1 - c1*c1) * e2 * e2 +
                        (1 - c2*c2) * e1 * e1 +
                        2 * c1 * c2 * e1 * e2;

        if (discr < 0) return new Circle[]{null, null};

        double root = Math.sqrt(discr);
        double h = rK * s1 + c1*e1 + c2*e2;

        double r1 = (h + root) / denom;
        double r2 = (h - root) / denom;

        Circle[] result = new Circle[2];

        if (r1 > 0) {
            double x = c1 * r1 + d1;
            double y = c2 * r1 + d2;
            result[0] = new Circle(x, y, r1, LineColor.CYAN_3);
        }

        if (r2 > 0 && discr > 1e-12) {
            double x = c1 * r2 + d1;
            double y = c2 * r2 + d2;
            result[1] = new Circle(x, y, r2,  LineColor.CYAN_3);
        }

        return result;
    }

    private void processApollonius_CCC() {
        final double EPS = 1e-6;
        final double MIN_R = 1e-3;
        final double MAX_R = 1e6;

        int[] signs = {-1, 1};

        for (int s1 : signs) {
            for (int s2 : signs) {
                for (int s3 : signs) {

                    Circle[] sols = solveCCC(s1, s2, s3);

                    for (Circle c : sols) {
                        if (c == null) continue;

                        if (c.getR() < MIN_R || c.getR() > MAX_R) continue;

                        if (!isDuplicate(c, indicators, EPS)) {
                            indicators.add(c);
                        }
                    }
                }
            }
        }
    }

    private Circle[] solveCCC(int s1, int s2, int s3) {
        double x1 = k1.getX(), y1 = k1.getY(), r1 = k1.getR();
        double x2 = k2.getX(), y2 = k2.getY(), r2 = k2.getR();
        double x3 = k3.getX(), y3 = k3.getY(), r3 = k3.getR();
        double sr1 = s1 * r1;
        double sr2 = s2 * r2;
        double sr3 = s3 * r3;

        double Ka = -Math.pow(sr1, 2) + Math.pow(sr2, 2) + Math.pow(x1, 2) - Math.pow(x2, 2) + Math.pow(y1, 2) - Math.pow(y2, 2);
        double Kb = -Math.pow(sr1, 2) + Math.pow(sr3, 2) + Math.pow(x1, 2) - Math.pow(x3, 2) + Math.pow(y1, 2) - Math.pow(y3, 2);

        double D = x1 * (y2 - y3) + x2 * (y3 - y1) + x3 * (y1 - y2);

        // avoid degenerate case
        if (Math.abs(D) < 1e-9) return new Circle[]{null, null};

        double A0 = (Ka * (y1 - y3) + Kb * (y2 - y1)) / (2 * D);
        double B0 = -(Ka * (x1 - x3) + Kb * (x2 - x1)) / (2 * D);

        double A1 = -(sr1 * (y2 - y3) + sr2 * (y3 - y1) + sr3 * (y1 - y2)) / D;
        double B1 = (sr1 * (x2 - x3) + sr2 * (x3 - x1) + sr3 * (x1 - x2)) / D;

        double C0 = Math.pow(A0, 2) - 2 * A0 * x1 + Math.pow(B0, 2) - 2 * B0 * y1 - Math.pow(sr1, 2) + Math.pow(x1, 2) + Math.pow(y1, 2);
        double C1 = A0 * A1 - A1 * x1 + B0 * B1 - B1 * y1 - sr1;
        double C2 = Math.pow(A1, 2) + Math.pow(B1, 2) - 1;

        double discriminant = C1 * C1 - C0 * C2;

        if (discriminant < 0) return new Circle[]{null, null};

        double sqrt = Math.sqrt(discriminant);
        double[] solutions = { (-C1 - sqrt) / C2, (-C1 + sqrt) / C2 };

        Circle[] result = new Circle[2];

        int i = 0;
        for (double r : solutions) {
            if (Double.isNaN(r) || Double.isInfinite(r)) continue;

            double x = A0 + A1 * r;
            double y = B0 + B1 * r;
            result[i]=new Circle(x, y, Math.abs(r), LineColor.PURPLE_8);
            i++;
        }
        return result;
    }

    private double[] coeffLinEquPar(LineSegment g, int s) {
        double x1 = g.getA().getX();
        double y1 = g.getA().getY();
        double x2 = g.getB().getX();
        double y2 = g.getB().getY();

        double dx = x2 - x1;
        double dy = y2 - y1;

        double r0 = dy * x1 - dx * y1;
        double r1 = dy;
        double r2 = -dx;
        double r3 = -s * Math.sqrt(dx*dx + dy*dy);

        return new double[]{r0, r1, r2, r3};
    }

    private double[][] solution23(double[][] e) {

        double det0 = e[0][1]*e[1][2] - e[1][1]*e[0][2];

        if (Math.abs(det0) < 1e-9) {
            return new double[][]{{0,0},{0,0}}; // degenerate (parallel lines)
        }

        double det1r = e[1][3]*e[0][2] - e[0][3]*e[1][2];
        double det1  = e[0][0]*e[1][2] - e[1][0]*e[0][2];

        double det2r = e[1][1]*e[0][3] - e[0][1]*e[1][3];
        double det2  = e[0][1]*e[1][0] - e[1][1]*e[0][0];

        return new double[][]{
                {det1r / det0, det1 / det0},
                {det2r / det0, det2 / det0}
        };
    }
    private boolean isDuplicate(Circle c, List<Circle> list, double eps) {
        for (Circle other : list) {
            double dx = c.getX() - other.getX();
            double dy = c.getY() - other.getY();
            double dr = c.getR() - other.getR();

            if (Math.sqrt(dx*dx + dy*dy) < eps && Math.abs(dr) < eps) {
                return true;
            }
        }
        return false;
    }
    private double[] coeffLinEqu(Circle k1, int s1, Circle k2, int s2) {

        double x1 = k1.getX(), y1 = k1.getY(), r1 = k1.getR();
        double x2 = k2.getX(), y2 = k2.getY(), r2 = k2.getR();

        double r0 = x2*x2 - x1*x1 + y2*y2 - y1*y1 + r1*r1 - r2*r2;
        double r1c = 2 * (x2 - x1);
        double r2c = 2 * (y2 - y1);
        double r3 = 2 * (s1*r1 - s2*r2);

        return new double[]{r0, r1c, r2c, r3};
    }
}
