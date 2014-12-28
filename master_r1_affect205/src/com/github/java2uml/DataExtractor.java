package com.github.java2uml;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import net.sourceforge.plantuml.SourceStringReader;

public class DataExtractor {
	
	/**
     * Извлечение данных из множества классов для построения uml диаграмм в формате plantuml
     * @param classes - ножество загруженный классов
     * @return
     */
    public static String extract(final Set<Class> classes) {

        // текст в формате plantuml - начало сборки
        StringBuilder source = new StringBuilder();
        source.append("@startuml\n");
        source.append("skinparam classAttributeIconSize 0\n");
        
        // таблица пакетов для всех входящих классов
        Map<String, String> packages = new TreeMap<>();
        for (Class clazz : classes) {
        	packages.put(getPackageName(clazz.getCanonicalName()), "");
        }
        
        // множество связей для бросаемых исключений: исключение <.. класс
        Set<String> throwLinks = new HashSet<String>();
        
        // хэш точек входа: определяем по public static методу с именем main
        Map<String, String> entryPoints = new HashMap<>();
        
        // объявление классов...
        for (Class clazz : classes) {
        	// анонимные классы и классы, доюавленные компилятором, пока игнорируем
        	if (clazz.isAnonymousClass() || clazz.isSynthetic()) {
        		continue;
        	}
        	
        	// получение информации о классе
        	String className 	= clazz.getCanonicalName();
            String classPack	= getPackageName(clazz.getCanonicalName());
            
            // объявляем класс и его содержимое
            StringBuilder res = new StringBuilder();
            res.append(getClassModifiers(classes, clazz));
            
            // реализуемые интерфейсы, не попавшие во входное множество
            StringBuilder outerInterfaces = new StringBuilder();
            for (Class inter : clazz.getInterfaces()) {
            	if (!classes.contains(inter)) {
            		// внешний интерфейс - укажем
            		outerInterfaces.append(inter.getSimpleName());
            		outerInterfaces.append(", ");
            	}
            }
            if (!outerInterfaces.toString().isEmpty()) {
            	String impl = outerInterfaces.toString(); 
            	res.append(" <<");
            	res.append(impl.substring(0, impl.length()-2));
            	res.append(">> ");
            }
            res.append(" {\n"); 
            
            // буфер статических членов класса
            StringBuilder staticMembers = new StringBuilder();
            
            // получение информации о полях
            Field[] fields = clazz.getDeclaredFields();
            res.append(".. Fields ..\n");
            for (Field field : fields) {
            	if (field.isSynthetic()) {
                	// выводим только объявленные структуры
                	continue;
                }
            	if (Modifier.isStatic(field.getModifiers())) {
            		// статические члены в конец объявления
            		staticMembers.append(getMemberModifiers(field.getModifiers()));
            		staticMembers.append(field.getName());
            		staticMembers.append(" : ");
            		staticMembers.append(field.getType().getSimpleName());
            		staticMembers.append("\n");
            		continue;
            	}
            	res.append(getMemberModifiers(field.getModifiers()));
            	res.append(field.getName());
            	res.append(" : ");
            	res.append(field.getType().getSimpleName());
            	res.append("\n");
            }

            // получение информации методах
            Method[] methods = clazz.getDeclaredMethods();
            res.append(".. Methods ..\n");
            for (Method method : methods) {
            	if (method.isSynthetic()) {
            		// выводим только объявленные структуры
            		continue;
            	}
            	if (Modifier.isStatic(method.getModifiers())) {
            		// статические члены в конец объявления
            		staticMembers.append(getMemberModifiers(method.getModifiers()));
            		staticMembers.append(method.getName());
            		staticMembers.append("()");
            		staticMembers.append(" : ");
            		staticMembers.append(method.getReturnType().getSimpleName());
            		staticMembers.append("\n");
            		
            		// определяем точку входа
            		if (Modifier.isPublic(method.getModifiers()) && method.getName().equals("main")) {
            			entryPoints.put(clazz.getCanonicalName(), "");
            		}
            		continue;
            	}
            	res.append(getMemberModifiers(method.getModifiers()));
                res.append(method.getName());
                res.append("()");
                res.append(" : ");
                res.append(method.getReturnType().getSimpleName());
                res.append("\n");
                
                // бросает ли метод исключение
                for (Class exception : method.getExceptionTypes()) {
                	if (classes.contains(exception)) {
                		// добавляем связь о брошенном исключении
                		String link = exception.getCanonicalName();
                		link += " <.. ";
                		link += className;
                		link += "\n";
                		throwLinks.add(link);
                	}
                }
            }
            
            // инфорация о статике
            res.append(".. Static ..\n");
            res.append(staticMembers.toString());
            
            // закрываем класс
            res.append("}\n");
            
            // добавляем класс в таблицу пакетов (класс с точками входа идут в отдельный пакет)
            if (entryPoints.containsKey(clazz.getCanonicalName())) {
            	entryPoints.put(clazz.getCanonicalName(), res.toString());
            } else {
            	packages.put(classPack, packages.get(classPack) + res.toString());
            }
        }
        
        // вывод объявленных классов с учетом пакетов и их влроженности 
        List<String> packList = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        for (Entry<String, String> entry : packages.entrySet()) {
        	if (entry.getValue().trim().isEmpty()) {
        		// добавлять нечего - пропускаем
        		continue;
        	}

        	if (packList.isEmpty()) {
        		// список пуст - добавим текущий пакет
        		packList.add(entry.getKey());
        		buffer.append("package ");
        		buffer.append(entry.getKey());
        		buffer.append(" #");
        		buffer.append(getPackageColor(0));
        		buffer.append(" {\n");		
        	} else {
        		// индекс - является ли текущий пакет пакетом из списка
        		int packNdx = -1;
        		for (int i=0; i < packList.size(); ++i) {
        			if (entry.getKey().contains(packList.get(i))) {
        				packNdx = i;
        			}
        		}
        		if (packNdx > -1) {
        			// пакет вложен - буферизуем пакеты, в которые не входит текущий
        			for (int i=packList.size()-1; i >= packNdx; --i) {
        				if (i==packNdx) {
        					// первый пакет не учитываем
        					continue;
        				}
        				String pack = packList.get(i);
                		buffer.append(packages.get(pack));
                		buffer.append("\n");
                		buffer.append("}\n");
        			}
        			
        			// удаляем буфиризированные пакеты
        			List<String> rest = new ArrayList<>();
        			rest.addAll(packList.subList(0, packNdx+1));
        			packList.clear();
        			for (String pack : rest) {
        				packList.add(pack);
        			}
        			
        			// добавляем текущий пакет
        			packList.add(entry.getKey());
        			buffer.append("package ");
            		buffer.append(entry.getKey());
            		buffer.append(" #");
            		buffer.append(getPackageColor(packList.size()));
            		buffer.append(" {\n");
        		} else {
        			// пакет не вложен - буферезуем весь список
        			for (int i=packList.size()-1; i >= 0; --i) {
        				String pack = packList.get(i);
                		buffer.append(packages.get(pack));
                		buffer.append("\n");
                		buffer.append("}\n");
        			}
        			
        			// очищаем список
        			packList.clear();
        			
        			// добавляем новый пакет и буферезуем его
        			packList.add(entry.getKey());
        			buffer.append("package ");
            		buffer.append(entry.getKey());
            		buffer.append(" #");
            		buffer.append(getPackageColor(packList.size()));
            		buffer.append(" {\n");
        		}
        	}
        }
        
        if (!packList.isEmpty()) {
        	// добавим в буфер последние пакеты
        	for (int i=packList.size()-1; i >= 0; --i) {
				String pack = packList.get(i);
        		buffer.append(packages.get(pack));
        		buffer.append("\n");
        		
        		buffer.append("}\n");
			}
        }
        
        // очищаем список пакетов
		packList.clear();
		
		// добавляем содерфимое буфера в сборщик
		source.append(buffer.toString());
		
		// определение точек входа
		source.append("package Entry-Points <<Cloud>> {\n");
		for (Entry entry : entryPoints.entrySet()) {
			source.append(entry.getValue());
			source.append("\n");
		}
		source.append("}\n");
		        
        // определение межклассовых связей
        for (Class clazz : classes) {
        	if ( clazz.getSimpleName().isEmpty() ) {
            	continue;
            }
        	        	
        	//имя класса
        	String className = clazz.getCanonicalName();
        	
        	// получение супер класса и реализованных интерфейсов
            Class superClass = clazz.getSuperclass();
            Class[] interfaces = clazz.getInterfaces();
            
            // множество структур, объявленных внутри класса
            Set<Class> declaredClasses = new HashSet<Class>();
            declaredClasses.addAll(Arrays.asList(clazz.getDeclaredClasses()));
            
            // пакет текущего класса
            String classPack = getPackageName(clazz.getCanonicalName());
            
            // объявление связей...
            if (classes.contains(superClass)) {
                // супер класс доступен во множестве - добавим связь
            	source.append(superClass.getCanonicalName());
                source.append(" <|-- ");
                source.append(className);
                source.append("\n");
            }
            for (Class interfc : interfaces) {
                if (classes.contains(interfc)) {
                    // интерфейс доступен во множестве - добавим связь
                	source.append(interfc.getCanonicalName());
                    source.append(" <|.. ");
                    source.append(className);
                    source.append("\n");
                } 
            }

            // получение внешних классов, являющихся полями clazz, объявленных вне clazz
            Field[] fieldClasses = clazz.getDeclaredFields();
            for (Field fieldClass : fieldClasses) {
            	if (fieldClass.getType() instanceof Object) {
                    if (classes.contains(fieldClass.getType())) {
                    	if (className.equals(fieldClass.getType().getCanonicalName())) {
                    		// связь на самого себя не учитываем
                    		continue;
                    	}
                    	if (fieldClass.getType().isEnum()) {
                    		continue;
                    	}
                    	if ( isDeclared(fieldClass.getType(), clazz) ) {
                    		// clazz объявлен внутри fieldClass - связь не учитываем
                    		continue;
                    	}
                        // поле есть внешний класс - добавляем связь агрегирование
                    	source.append(className);
                        source.append(" o-- ");
                        source.append(fieldClass.getType().getCanonicalName());
                        source.append("\n");
                    }
                }
            }
            
            // получение внутренних классов, объявленных внутри clazz
            for ( Class declaredClass : declaredClasses ) {
            	if (classes.contains(declaredClass)) {
            		// связь через композицию
            		source.append(className);
                    source.append(" *-- ");
                    source.append(declaredClass.getCanonicalName());
                    source.append("\n");
            	}
            }
        }
        
        // брошенные исключения
        for (String link : throwLinks) {
        	source.append(link);
        }
                
        // конец сборки
        source.append("@enduml\n");
        return source.toString();
    }
    
    /**
     * Генерация диаграммы классов
     * @param source - исходный текст классов на языке plantuml
     */
    public static void generate(final String source, final String fileName) {
        try {
            File file = new File(fileName);
            if (!file.exists()) {
                file.createNewFile();
            }

            // поток вывода для диаграммы
            OutputStream png = new FileOutputStream(file);

            // генератор диаграмм
            SourceStringReader reader = new SourceStringReader(source);

            // генерация жиаграммы
            String desc = reader.generateImage(png);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
        
    /**
     * Получение строки с именем пакета (обрезаем из canonicalName строку с именем класса)
     * @param className - полное имя класса
     * @return
     */
    private static String getPackageName(final String className) {
    	if ( className == null ) {
    		return "default_pack";
    	}
    	int classNdx = className.lastIndexOf(".");
    	if (classNdx > -1) {
    		return className.substring(0, classNdx);
    	}
    	return "default_pack";
    }
    
    /**
     * Проверка объявления класса child в теле класса parent
     * @param parent
     * @param child
     * @return
     */
    private static boolean isDeclared(final Class parent, final Class child) {
    	if (parent == null || child == null ) {
    		return false;
    	}
    	Set<Class> classes = new HashSet<Class>();
    	classes.addAll(Arrays.asList(parent.getDeclaredClasses()));
    	if (classes.contains(child)) {
    		return true;
    	}
    	return false;
    }
        
    /**
     * Получение модификаторов членов класса
     * @param mod
     * @return
     */
    private static String getMemberModifiers(final int mod) {
        // значение по умолчанию - package private
        String modStr = "~";
        if (Modifier.isPrivate(mod)) {
            modStr = "-";
        }
        if (Modifier.isProtected(mod)) {
            modStr = "#";
        }
        if (Modifier.isPublic(mod)) {
            modStr = "+";
        }
        if (Modifier.isAbstract(mod)) {
            modStr += " {abstract} ";
        }
        if (Modifier.isStatic(mod)) {
            modStr += " {static} ";
        }
        return modStr;
    }

    /**
     * Получение модификаторов класса
     * @param classes - множество всех классов
     * @param clazz - текущий класс для построения
     * @return
     */
    private static String getClassModifiers(final Set<Class> classes, final Class clazz) {
    	String unknown 	= getPackageName(clazz.getCanonicalName()) + ".Unknown";
    	String known	= getPackageName(clazz.getCanonicalName()) + "." + clazz.getSimpleName();
    	String className 	= (clazz.getSimpleName().isEmpty()) ? unknown : known;
    	className = clazz.getCanonicalName();
        String modStr 		= "class " + className;
        if (Modifier.isAbstract(clazz.getModifiers())) {
            modStr = "abstract class " + className + " ";
        }
        if (clazz.isInterface()) {
            modStr = "interface " + className + " ";
        }
        if (clazz.isEnum()) {
        	modStr = "enum " + className + " ";
        }
        if (className.toLowerCase().contains("exception")) {
        	// исключения получают особый вид
        	modStr = "class " + className + " << (E,yellow) >> ";
        }
        return modStr;
    }
    
    /**
     * Получение цвета пакета в зависимости от уровня вложенности
     * @param level - чем больше уровень, тем темнее цвет (но не более 10 уровней)
     * @return
     */
    public static String getPackageColor(final int level) {
    	if (level <= 0) {
    		// по-умолчанию возвращаем белый
    		return Integer.toHexString(0xffffff);
    	}
    	int color = 0xffffff;
    	for(int i=1; i < level%11; ++i) {
    		// с каждым уровнем делаем чуть темнее
    		color -= 0x111515;
    	}
    	return Integer.toHexString(color);
    }
}