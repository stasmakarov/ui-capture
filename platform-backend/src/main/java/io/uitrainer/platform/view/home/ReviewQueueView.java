package io.uitrainer.platform.view.home;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.core.TimeSource;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.ViewController;
import io.uitrainer.platform.security.PlatformViews;
import io.uitrainer.platform.service.LessonLifecycleService;
import io.uitrainer.platform.view.main.MainView;
import org.springframework.beans.factory.annotation.Autowired;

/** Стартовый экран проверяющего: очередь приёмки. */
@Route(value = "cabinet/review", layout = MainView.class)
@ViewController(id = PlatformViews.REVIEW_QUEUE)
public class ReviewQueueView extends StandardView {

    @Autowired
    private DataManager dataManager;
    @Autowired
    private LessonLifecycleService lifecycle;
    @Autowired
    private TimeSource timeSource;

    public ReviewQueueView() {
        getContent().setWidthFull();
        getContent().setPadding(true);
        getContent().setSpacing(true);
    }

    @Override
    public String getPageTitle() {
        return "Очередь проверки";
    }

    @io.jmix.flowui.view.Subscribe
    public void onInit(InitEvent event) {
        H2 title = new H2("Очередь проверки");
        title.getStyle().set("margin", "0");
        getContent().add(title, new ReviewQueuePanel(dataManager, lifecycle, timeSource));
    }
}
