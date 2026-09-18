package ru.ugk.schedule.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.ugk.schedule.service.CatalogService;
import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminController {
    private final CatalogService catalog;
    public AdminController(CatalogService catalog){ this.catalog = catalog; }

    @GetMapping("/login") public String login(){ return "admin/login"; }
    @GetMapping public String dashboard(Model model){
        model.addAttribute("levels", catalog.allLevels());
        model.addAttribute("courses", catalog.allCourses());
        model.addAttribute("groups", catalog.allGroups());
        return "admin/dashboard";
    }
    @PostMapping("/levels") public String saveLevel(@RequestParam(required=false) Long id,@RequestParam String name,@RequestParam Integer maxCourses,
                                                     @RequestParam(defaultValue="0") Integer sortOrder,@RequestParam(defaultValue="false") boolean active, RedirectAttributes ra){
        try { catalog.saveLevel(id,name,maxCourses,sortOrder,active); ra.addFlashAttribute("message","Уровень образования сохранён"); }
        catch(Exception e){ ra.addFlashAttribute("error",e.getMessage()); } return "redirect:/admin";
    }
    @PostMapping("/levels/{id}/delete") public String deleteLevel(@PathVariable Long id, RedirectAttributes ra){ try{catalog.deleteLevel(id);}catch(Exception e){ra.addFlashAttribute("error","Нельзя удалить: есть связанные данные");} return "redirect:/admin"; }
    @PostMapping("/courses") public String saveCourse(@RequestParam(required=false) Long id,@RequestParam Long levelId,@RequestParam Integer number,
                                                       @RequestParam String name,@RequestParam(defaultValue="false") boolean active,RedirectAttributes ra){
        try{catalog.saveCourse(id,levelId,number,name,active);ra.addFlashAttribute("message","Курс сохранён");}catch(Exception e){ra.addFlashAttribute("error",e.getMessage());} return "redirect:/admin";
    }
    @PostMapping("/courses/{id}/delete") public String deleteCourse(@PathVariable Long id,RedirectAttributes ra){try{catalog.deleteCourse(id);}catch(Exception e){ra.addFlashAttribute("error","Нельзя удалить: есть связанные данные");}return "redirect:/admin";}
    @PostMapping("/courses/bulk")
    public String createCourses(@RequestParam(required=false) List<Long> levelIds,
                                @RequestParam Integer number, @RequestParam(defaultValue="") String name,
                                @RequestParam(defaultValue="false") boolean active, RedirectAttributes ra) {
        try {
            var result = catalog.createCourses(levelIds, number, name, active);
            ra.addFlashAttribute("message", "Добавлено курсов: " + result.created() + ". Уже существовали: " + result.skipped());
        } catch (IllegalArgumentException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin";
    }

    @PostMapping("/groups/bulk")
    public String createGroups(@RequestParam(required=false) List<Long> courseIds, @RequestParam String name,
                               @RequestParam(defaultValue="false") boolean active, RedirectAttributes ra) {
        try {
            var result = catalog.createGroups(courseIds, name, active);
            ra.addFlashAttribute("message", "Добавлено групп: " + result.created() + ". Уже существовали: " + result.skipped());
        } catch (IllegalArgumentException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin";
    }
    @PostMapping("/groups") public String saveGroup(@RequestParam(required=false) Long id,@RequestParam Long courseId,@RequestParam String name,
                                                     @RequestParam(defaultValue="false") boolean active,RedirectAttributes ra){
        try{catalog.saveGroup(id,courseId,name,active);ra.addFlashAttribute("message","Группа сохранена");}catch(Exception e){ra.addFlashAttribute("error",e.getMessage());}return "redirect:/admin";
    }
    @PostMapping("/groups/{id}/delete") public String deleteGroup(@PathVariable Long id,RedirectAttributes ra){try{catalog.deleteGroup(id);}catch(Exception e){ra.addFlashAttribute("error","Нельзя удалить: есть расписание");}return "redirect:/admin";}
}
