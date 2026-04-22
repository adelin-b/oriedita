package oriedita.editor.handler;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import oriedita.editor.canvas.MouseMode;
import oriedita.editor.drawing.tools.Camera;
import oriedita.editor.drawing.tools.DrawingUtil;
import oriedita.editor.handler.step.ObjCoordStepNode;
import oriedita.editor.handler.step.StepMouseHandler;
import origami.crease_pattern.OritaCalc;
import origami.crease_pattern.element.Circle;
import origami.crease_pattern.element.LineColor;
import origami.crease_pattern.element.Point;
import java.awt.Graphics2D;

enum CircleDrawFrom3CirclesStep {
        SELECT_1ST_CIRCLE,
        SELECT_2ND_CIRCLE,
        SELECT_3RD_CIRCLE,
        SELECT_CIRCLE,
    }

    @ApplicationScoped
    @Handles(MouseMode.CIRCLE_DRAW_FROM_3_CIRCLES_108)
    public class MouseHandlerCircleDrawFrom3Circles extends StepMouseHandler<CircleDrawFrom3CirclesStep> {
        private Circle circle1, circle2, circle3;

        @Inject
        public MouseHandlerCircleDrawFrom3Circles() {
            super(CircleDrawFrom3CirclesStep.SELECT_1ST_CIRCLE);
            steps.addNode(ObjCoordStepNode.createNode_MD_R(CircleDrawFrom3CirclesStep.SELECT_1ST_CIRCLE,
                    this::move_drag_select_first_circle,
                    this::release_select_first_circle));
            steps.addNode(ObjCoordStepNode.createNode_MD_R(CircleDrawFrom3CirclesStep.SELECT_2ND_CIRCLE,
                    this::move_drag_select_second_circle,
                    this::release_select_second_circle));
            steps.addNode(ObjCoordStepNode.createNode_MD_R(CircleDrawFrom3CirclesStep.SELECT_3RD_CIRCLE,
                    this::move_drag_select_third_circle,
                    this::release_select_third_circle));
        }

        @Override
        public void drawPreview(Graphics2D g2, Camera camera, DrawingSettings settings) {
            super.drawPreview(g2, camera, settings);
            DrawingUtil.drawCircleStep(g2, circle1, camera);
            DrawingUtil.drawCircleStep(g2, circle2, camera);
            DrawingUtil.drawCircleStep(g2, circle3, camera);
        }

        @Override
        public void reset() {
            resetStep();
            circle1 = null;
            circle2 = null;
            circle3 = null;
        }

        // Select first circle
        private void move_drag_select_first_circle(Point p) {
            Circle tmpCircle = d.getClosestCircleMidpoint(p);
            if (OritaCalc.distance_circumference(p, tmpCircle) < d.getSelectionDistance()) {
                circle1 = new Circle(tmpCircle);
                circle1.setColor(LineColor.GREEN_6);
            } else
                circle1 = null;
        }

        private CircleDrawFrom3CirclesStep release_select_first_circle(Point p) {
            if (circle1 == null)
                return CircleDrawFrom3CirclesStep.SELECT_1ST_CIRCLE;
            else
                return CircleDrawFrom3CirclesStep.SELECT_2ND_CIRCLE;
        }

        // Select second circle
        private void move_drag_select_second_circle(Point p) {
            Circle tmpCircle = d.getClosestCircleMidpoint(p);
            if (OritaCalc.distance_circumference(p, tmpCircle) < d.getSelectionDistance()) {
                circle2 = new Circle(tmpCircle);
                circle2.setColor(LineColor.GREEN_6);
            } else
                circle2 = null;
        }

        private CircleDrawFrom3CirclesStep release_select_second_circle(Point p) {
            if (circle2 == null)
                return CircleDrawFrom3CirclesStep.SELECT_2ND_CIRCLE;
            else
                return CircleDrawFrom3CirclesStep.SELECT_3RD_CIRCLE;
        }

        // Select third circle
        private void move_drag_select_third_circle(Point p) {
            Circle tmpCircle = d.getClosestCircleMidpoint(p);
            if (OritaCalc.distance_circumference(p, tmpCircle) < d.getSelectionDistance()) {
                circle3 = new Circle(tmpCircle);
                circle3.setColor(LineColor.GREEN_6);
            } else
                circle3 = null;
        }

        private CircleDrawFrom3CirclesStep release_select_third_circle(Point p) {
            if (circle3 == null)
                return CircleDrawFrom3CirclesStep.SELECT_3RD_CIRCLE;
            else {
                processResultCircle();
                reset();
                return CircleDrawFrom3CirclesStep.SELECT_1ST_CIRCLE;
            }
        }


        private void processResultCircle() {

            double x1 = circle1.getX(), y1 = circle1.getY(), r1 = circle1.getR();
            double x2 = circle2.getX(), y2 = circle2.getY(), r2 = circle2.getR();
            double x3 = circle3.getX(), y3 = circle3.getY(), r3 = circle3.getR();


            double Ka = - Math.pow(r1, 2) + Math.pow(r2, 2)
                        + Math.pow(x1, 2) - Math.pow(x2, 2)
                        + Math.pow(y1, 2) - Math.pow(y2, 2);

            double Kb = - Math.pow(r1, 2) + Math.pow(r3, 2)
                        + Math.pow(x1, 2) - Math.pow(x3, 2)
                        + Math.pow(y1, 2) - Math.pow(y3, 2);

            double D = x1 * (y2 - y3) + x2 * (y3 - y1) + x3 * (y1 - y2);

            double A0 =  (Ka * (y1 - y3) + Kb * (y2 - y1)) / (2 * D);
            double B0 = -(Ka * (x1 - x3) + Kb * (x2 - x1)) / (2 * D);

            double A1 = -(r1 * (y2 - y3) + r2 * (y3 - y1) + r3 * (y1 - y2)) / D;
            double B1 =  (r1 * (x2 - x3) + r2 * (x3 - x1) + r3 * (x1 - x2)) / D;

            double C0 = Math.pow(A0, 2) - 2 * A0 * x1
                      + Math.pow(B0, 2) - 2 * B0 * y1
                      - Math.pow(r1, 2) + Math.pow(x1, 2) + Math.pow(y1, 2);

            double C1 = A0 * A1 - A1 * x1
                      + B0 * B1 - B1 * y1 - r1;

            double C2 = Math.pow(A1, 2) + Math.pow(B1, 2) - 1;

            // This has 2 solutions, the outer one is usually the desirable one
            double r_outer = (-C1 - Math.sqrt(Math.pow(C1, 2) - C0 * C2)) / C2;
            double r_inner = (-C1 + Math.sqrt(Math.pow(C1, 2) - C0 * C2)) / C2;

            if(!Double.isNaN(r_outer) && !Double.isInfinite(r_outer)) {
                double x = A0 + A1 * r_outer;
                double y = B0 + B1 * r_outer;
                Circle c1 = new Circle(x, y, Math.abs(r_outer), LineColor.CYAN_3);
                d.addCircle(c1);
            }

            if(!Double.isNaN(r_inner) && !Double.isInfinite(r_inner)) {
                double x = A0 + A1 * r_inner;
                double y = B0 + B1 * r_inner;
                Circle c2 = new Circle(x, y, Math.abs(r_inner), LineColor.CYAN_3);
                d.addCircle(c2);
            }
            if(!Double.isNaN(r_outer) && !Double.isInfinite(r_outer) &&
               !Double.isNaN(r_inner) && !Double.isInfinite(r_inner)) {
                d.record();
            }
        }
    }
