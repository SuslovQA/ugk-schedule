insert into education_levels(name,max_courses,sort_order) values
('Бакалавриат',4,10),('Специалитет',5,20),('Магистратура',2,30),('Аспирантура',4,40),('Ассистентура-стажировка',2,50);

insert into courses(education_level_id,number,name)
select e.id,n,n || ' курс' from education_levels e cross join generate_series(1,e.max_courses) n;

insert into study_groups(course_id,name)
select c.id,g.name from courses c cross join (values ('ДХО'),('ОНИ'),('Струнные'),('Духовые-ударные')) g(name)
where c.number=1 and c.education_level_id=(select id from education_levels where name='Бакалавриат');
